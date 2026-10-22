package com.example.cache.service;

import com.example.cache.exception.CommandException;
import com.example.cache.model.Value;
import com.example.cache.model.ScoredMember;
import com.example.cache.model.SortedSetValue;
import com.example.cache.model.StringValue;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class Functions {
    private final Map<String, Value> database = new ConcurrentHashMap<>();
    private final Map<String, Long> expirations = new ConcurrentHashMap<>();
    private final PersistenceService persistence;
    private boolean replaying;
    private long commandTimeMillis = System.currentTimeMillis();

    public Functions(PersistenceService persistence) { this.persistence = persistence; }

    @PostConstruct
    public synchronized void load() {
        replaying = true;
        try {
            persistence.replayTimed((timestamp, command) -> executeAt(command, (long) (timestamp * 1000.0)));
        } finally {
            replaying = false;
            commandTimeMillis = System.currentTimeMillis();
        }
    }

    public synchronized String execute(String input) {
        return executeAt(input, System.currentTimeMillis());
    }

    private synchronized String executeAt(String input, long timestampMillis) {
        commandTimeMillis = timestampMillis;
        try { return executeCommand(input); }
        finally { if (!replaying) commandTimeMillis = System.currentTimeMillis(); }
    }

    private String executeCommand(String input) {
        if (input == null || input.isBlank()) throw new CommandException("Request Invalid");
        String[] tokens = input.trim().split("\\s+");
        String command = tokens[0].toUpperCase(Locale.ROOT);
        String[] a = Arrays.copyOfRange(tokens, 1, tokens.length);
        return switch (command) {
            case "PING" -> { arity(a, 0, 0); yield "PONG"; }
            case "GET" -> get(a);
            case "SET" -> set(a);
            case "DEL", "DELETE" -> delete(a);
            case "EXPIRE" -> expire(a);
            case "TTL" -> ttl(a);
            case "ZADD" -> zadd(a);
            case "ZRANGE" -> range(a, false);
            case "ZREVRANGE" -> range(a, true);
            case "ZRANK" -> rank(a, false);
            case "ZREVRANK" -> rank(a, true);
            default -> "ERR Unknown or disabled command \"" + tokens[0] + "\"";
        };
    }

    private void arity(String[] a, int min, int max) { 
        if (a.length < min || a.length > max) throw new CommandException("Request Invalid");
    }
    private void log(String command, String... args) {
        if (!replaying) persistence.append(commandTimeMillis / 1000.0, command + (args.length == 0 ? "" : " " + String.join(" ", args)));
    }
    private void purge(String key) {
        Long at = expirations.get(key);
        if (at != null && at <= System.currentTimeMillis()) {
            database.remove(key);
            expirations.remove(key);
            log("DELETE", key);
        }
    }

    private String get(String[] a) {
        arity(a, 1, 1); purge(a[0]); Value value = database.get(a[0]);
        if (value == null) return "None";
        if (!(value instanceof StringValue s)) throw new CommandException("Returned value is not String");
        return s.value();
    }
    private String set(String[] a) {
        arity(a, 2, 10); String key=a[0], value=a[1]; purge(key);
        boolean nx=false, xx=false, keep=false; Long ttl=null;
        for (int i=2;i<a.length;i++) {
            switch (a[i].toUpperCase(Locale.ROOT)) {
                case "NX" -> nx=true; case "XX" -> xx=true; case "KEEPTTL" -> keep=true;
                case "EX", "PX" -> { if (++i>=a.length) throw new CommandException("Request Invalid"); try { long n=Long.parseLong(a[i]); if(n<0) throw new NumberFormatException(); ttl=n*(a[i-1].equalsIgnoreCase("EX")?1000L:1L); } catch(NumberFormatException e){throw new CommandException("Request Invalid");} }
                default -> throw new CommandException("Request Invalid");
            }
        }
        if ((nx&&xx)||(ttl!=null&&keep)) throw new CommandException("Request Invalid");
        if ((nx&&database.containsKey(key))||(xx&&!database.containsKey(key))) return "None";
        Long old=expirations.get(key); database.put(key,new StringValue(value));
        if (keep) { 
            if(old==null) expirations.remove(key); 
        } else if(ttl!=null) expirations.put(key,commandTimeMillis+ttl); 
        else expirations.remove(key);
        log("SET",a); return "OK";
    }
    private String delete(String[] a) {
        arity(a,1,1); 
        boolean had=database.remove(a[0])!=null; 
        expirations.remove(a[0]); 
        if(had)log("DELETE",a); 
        return had?"1":"0"; 
    }
    private String expire(String[] a) { 
        arity(a,2,2); purge(a[0]); 
        if(!database.containsKey(a[0]))return "0"; 
        try{
            expirations.put(a[0],commandTimeMillis+Long.parseLong(a[1])*1000L);
        }
        catch(NumberFormatException e){
            throw new CommandException("Request Invalid");
        }
        log("EXPIRE",a);return "1"; 
    }
    private String ttl(String[] a) {
        arity(a,1,1); 
        purge(a[0]); 
        if(!database.containsKey(a[0])||!expirations.containsKey(a[0]))return "None"; 
        return Double.toString((expirations.get(a[0])-System.currentTimeMillis())/1000.0); 
    }

    private String zadd(String[] a) {
        arity(a,3,64); String key=a[0]; 
        purge(key); 
        boolean nx=false,xx=false,ch=false,incr=false; 
        int pairsEnd=a.length;
        while(pairsEnd>1 && Set.of("NX","XX","CH","INCR").contains(a[pairsEnd-1].toUpperCase(Locale.ROOT))) 
            { 
                switch(a[--pairsEnd].toUpperCase(Locale.ROOT)){
                    case "NX"->nx=true;case "XX"->xx=true;
                    case "CH"->ch=true;
                case "INCR"->incr=true;
            } 
        }
        if(nx&&xx || (pairsEnd-1)%2!=0 || (incr && pairsEnd!=3)) throw new CommandException("Syntax Error in Command");
        Value existing=database.get(key);
        if(existing!=null&&!(existing instanceof SortedSetValue))throw new CommandException("Wrong Data Type");
        boolean wasMissing=existing==null;
        SortedSetValue set=wasMissing?new SortedSetValue():(SortedSetValue)existing;

        if(incr) throw new CommandException("Syntax Error in Command");
        if((nx&& !wasMissing)||(xx&&wasMissing))return "0";
        int changed=0,added=0;
        for(int i=1;i<pairsEnd;i+=2){
            double score;try{
                score=Double.parseDouble(a[i]);

            }
            catch(Exception e){
                throw new CommandException("Syntax Error in Command");
            } 
            String member=a[i+1]; 
            boolean before=set.contains(member); 
            if(set.add(score,member)){
                changed++;
                if(!before)added++;
            }
        }
        database.put(key,set);
        if(wasMissing)expirations.remove(key); 
        log("ZADD",a); 
        return Integer.toString(ch?changed:added);
    }
    private String rank(String[] a,boolean reverse){
        arity(a,2,2);
        purge(a[0]);Value v=database.get(a[0]);
        if(v==null)return "None";
        if(!(v instanceof SortedSetValue s))throw new CommandException(reverse?"Only sets can be queried for ZRevRank":"Only sets can be queried for Zrank");
        int r=s.rank(a[1]);return r<0?"None":Integer.toString(reverse?s.size()-r-1:r);
    }
    private String range(String[] a,boolean reverse){
        arity(a,3,4);
        purge(a[0]);
        Value v=database.get(a[0]);
        if(v==null)return "None";if(!(v instanceof SortedSetValue s))throw new CommandException("Only sets can be queried for ZRange");boolean scores=a.length==4&&a[3].equalsIgnoreCase("WITHSCORES");if(a.length==4&&!scores)throw new CommandException("Syntax Error in Command");int start,end;try{start=Integer.parseInt(a[1]);end=Integer.parseInt(a[2]);}catch(Exception e){throw new CommandException("Invalid format");}int n=s.size();if(start<0)start+=n;if(end<0)end+=n;start=Math.max(start,0);end=Math.min(end,n-1);if(start>=n||end<start)return "";List<String> result=new ArrayList<>();for(int i=start;i<=end;i++){ScoredMember m=s.get(reverse?n-1-i:i);result.add(scores?formatScore(m.score())+" "+m.member():m.member());}return String.join(", ",result);}
    private String formatScore(double score) { 
        return score == Math.rint(score) ? Long.toString((long) score) : Double.toString(score); 

    }

    @Scheduled(fixedDelayString = "${redis.clone.cleanup-interval-ms:1000}")
    public synchronized void activeCleanup() {
        for (int attempt = 0; attempt < 10 && database.size() > 100; attempt++) {
            List<String> keys = new ArrayList<>(database.keySet());
            if (keys.size() <= 10) return;
            for (int i = 0; i < 10; i++) {
                String key = keys.remove(ThreadLocalRandom.current().nextInt(keys.size()));
                purge(key);
            }
        }
    }

    public synchronized String currentStatus(){
        database.keySet().forEach(this::purge);
        return database.toString();
    }
}
