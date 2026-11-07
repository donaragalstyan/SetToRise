package com.settorise.news;

import com.settorise.config.AppConfig.Section;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Keyword tagging for tabs, issue topics and election detection. Simple and explainable. */
@Component
public class Classifier {

    private static final Pattern POLITICS = words(
            "congress", "congressional", "senate", "senator", "senators", "house speaker", "lawmakers?",
            "white house", "president", "presidential", "trump", "vance", "biden", "harris", "governor",
            "democrats?", "democratic", "republicans?", "gop", "supreme court", "legislation", "bill",
            "campaign", "election", "elections", "ballot", "voters?", "midterms?", "administration",
            "cabinet", "impeach\\w*", "filibuster", "shutdown", "attorney general", "doj", "fbi",
            "executive order", "veto", "partisan", "caucus", "mayor", "pentagon", "secretary");

    private static final Pattern GLOBAL = words(
            "ukraine", "russia\\w*", "putin", "zelensky\\w*", "china", "chinese", "beijing", "taiwan",
            "israel\\w*", "gaza", "hamas", "hezbollah", "iran\\w*", "syria\\w*", "lebanon", "yemen",
            "nato", "united nations", "u\\.n", "european union", "eu", "europe\\w*", "britain", "british",
            "uk", "france", "french", "germany", "german", "india\\w*", "pakistan", "japan\\w*", "korea\\w*",
            "mexico", "mexican", "canada", "canadian", "venezuela\\w*", "cuba\\w*", "brazil\\w*",
            "africa\\w*", "middle east", "foreign", "international", "global", "world leaders?",
            "diplomat\\w*", "embassy", "sanctions", "ceasefire", "refugees?", "summit");

    private static final Map<String, Pattern> TOPICS = new LinkedHashMap<>();

    static {
        TOPICS.put("Economy", words("econom\\w*", "inflation", "jobs report", "job market", "layoffs?", "hiring", "unemployment", "tariffs?", "taxe?s?",
                "prices?", "wages?", "federal reserve", "the fed", "interest rates?", "recession", "stocks?",
                "markets?", "budget", "deficit", "debt", "housing", "rent", "mortgage\\w*", "minimum wage"));
        TOPICS.put("Health care", words("health ?care", "medicare", "medicaid", "obamacare", "affordable care act",
                "insurance", "hospitals?", "drug prices", "prescription", "vaccines?", "public health", "cdc", "fda"));
        TOPICS.put("Climate & energy", words("climate", "emissions", "carbon", "energy", "oil", "gas prices",
                "renewable", "solar", "wind power", "epa", "wildfires?", "hurricanes?", "drought", "flooding"));
        TOPICS.put("Immigration", words("immigra\\w*", "border", "migrants?", "deport\\w*", "asylum",
                "ice agents?", "customs and border", "daca", "citizenship", "visas?"));
        TOPICS.put("Education", words("public schools?", "school (?:boards?|districts?|choice|funding|vouchers?|shootings?)", "students?", "colleges?(?! *(?:football|basketball|sports))(?<!electoral college)", "universit\\w*", "teachers?",
                "education", "student loans?", "campus", "curriculum"));
        TOPICS.put("Guns", words("guns?", "firearms?", "shootings?", "second amendment", "nra", "rifles?"));
        TOPICS.put("Crime & justice", words("crime", "police", "policing", "prisons?", "prosecutors?",
                "indicted", "indictment", "trial", "sentenced", "murder", "arrested", "justice department"));
        TOPICS.put("Tech & AI", words("artificial intelligence", "ai", "tech", "technology", "social media",
                "big tech", "privacy", "cyber\\w*", "tiktok", "algorithm\\w*", "chips?", "semiconductors?"));
        TOPICS.put("Abortion", words("abortion", "roe", "reproductive", "ivf", "planned parenthood"));
        TOPICS.put("Civil rights", words("civil rights", "voting rights", "transgender", "trans", "lgbtq?\\+?",
                "discrimination", "dei", "diversity", "racism", "racial", "free speech", "first amendment"));
    }

    private static final Pattern ELECTION = words(
            "elections?", "electoral", "ballots?", "voters?", "voting", "polling places?", "polls close",
            "midterms?", "primary elections?", "primaries", "runoff", "early vote", "early voting", "absentee",
            "mail-in", "election day", "ballot initiative", "ballot measure", "initiative \\d+",
            "proposition \\d+", "voter registration", "registered to vote");

    /** vote.gov is for US elections. A world story needs a US signal before we call it election-related. */
    private static final Pattern US_MARKERS = words(
            "u\\.s", "us", "america\\w*", "congress\\w*", "senate", "house", "midterms?", "governor",
            "state", "county", "trump", "democrats?", "republicans?", "gop", "washington");

    public record Tags(boolean politics, boolean global, List<String> topics, boolean electionRelated) { }

    public Tags tag(Story s) {
        String text = (s.headline() + " " + s.summary()).toLowerCase(Locale.ROOT);
        List<String> topics = new ArrayList<>();
        for (Map.Entry<String, Pattern> t : TOPICS.entrySet()) {
            if (t.getValue().matcher(text).find()) {
                topics.add(t.getKey());
            }
        }
        boolean politics = s.section() == Section.POLITICS || POLITICS.matcher(text).find();
        boolean global = s.section() == Section.WORLD || GLOBAL.matcher(text).find();
        boolean election = ELECTION.matcher(text).find();
        if (election && global && !US_MARKERS.matcher(text).find()) {
            election = false;
        }
        return new Tags(politics, global, topics, election);
    }

    public static List<String> topicNames() {
        return List.copyOf(TOPICS.keySet());
    }

    private static Pattern words(String... terms) {
        return Pattern.compile("\\b(?:" + String.join("|", terms) + ")\\b");
    }
}
