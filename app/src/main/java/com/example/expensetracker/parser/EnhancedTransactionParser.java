package com.example.expensetracker.parser;

import android.util.Base64;
import android.util.Log;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.models.Transaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced TransactionParser with NLP-inspired techniques for better parsing of bank SMS messages
 */
public class EnhancedTransactionParser {
    private static final String TAG = "EnhancedTransactionParser";

    // ===== Bank Identification Patterns =====
    private final Map<String, List<String>> BANK_IDENTIFIERS = new HashMap<>();

    // ===== Transaction Type Keywords =====
    private final Map<String, List<String>> TRANSACTION_TYPE_KEYWORDS = new HashMap<>();

    // ===== Amount Patterns =====
    private final List<Pattern> AMOUNT_PATTERNS = new ArrayList<>();

    // ===== Date Patterns =====
    private final List<SimpleDateFormat> DATE_FORMATS = new ArrayList<>();
    private final List<Pattern> DATE_PATTERNS = new ArrayList<>();

    // ===== Merchant/Purpose Indicators =====
    private final List<String> MERCHANT_INDICATORS = new ArrayList<>();
    private final List<String> PURPOSE_INDICATORS = new ArrayList<>();

    // ===== Transaction Method Keywords =====
    private final Map<String, List<String>> TRANSACTION_METHODS = new HashMap<>();

    // ===== Exclusion Patterns =====
    private final List<Pattern> EXCLUSION_PATTERNS = new ArrayList<>();

    // ===== Category Keywords =====
    private final Map<String, List<String>> CATEGORY_KEYWORDS = new HashMap<>();

    // ===== Bank-specific message formats =====
    private final Map<String, List<Pattern>> BANK_MESSAGE_FORMATS = new HashMap<>();

    // ===== Context tokens for parsing =====
    private enum TokenType {
        AMOUNT, DATE, BANK, MERCHANT, PURPOSE, TRANSACTION_TYPE, REFERENCE, UNKNOWN
    }

    /**
     * Class constructor - initializes all pattern dictionaries
     */
    public EnhancedTransactionParser() {
        initializeBankIdentifiers();
        initializeTransactionTypeKeywords();
        initializeAmountPatterns();
        initializeDateFormats();
        initializeDatePatterns();
        initializeMerchantAndPurposeIndicators();
        initializeTransactionMethods();
        initializeExclusionPatterns();
        initializeCategoryKeywords();
        initializeBankMessageFormats();
    }

    // ===== Pattern Initializers =====

    private void initializeBankIdentifiers() {
        // HDFC Bank
        BANK_IDENTIFIERS.put("HDFC", List.of(
                "hdfc", "hdfcbank", "hdfc bank", "hd-fc", "hdfcb"
        ));

        // SBI
        BANK_IDENTIFIERS.put("SBI", List.of(
                "sbi", "sbiinb", "sbiatm", "state bank", "state bank of india", "sbi bank", "onlinesbi"
        ));

        // ICICI Bank
        BANK_IDENTIFIERS.put("ICICI", List.of(
                "icici", "icicibank", "icici bank", "icicib"
        ));

        // Axis Bank
        BANK_IDENTIFIERS.put("AXIS", List.of(
                "axis", "axisbank", "axis bank", "axisbk"
        ));

        // Kotak Mahindra Bank
        BANK_IDENTIFIERS.put("KOTAK", List.of(
                "kotak", "kotakbank", "kotak bank", "kotakm", "kotakmb", "kmbl"
        ));

        // Yes Bank
        BANK_IDENTIFIERS.put("YES", List.of(
                "yes bank", "yesbank", "ybl", "yesb"
        ));

        // IDBI Bank
        BANK_IDENTIFIERS.put("IDBI", List.of(
                "idbi", "idbibank", "idbi bank"
        ));

        // Bank of Baroda
        BANK_IDENTIFIERS.put("BOB", List.of(
                "bob", "bank of baroda", "bbank", "bkofb"
        ));

        // Canara Bank
        BANK_IDENTIFIERS.put("CANARA", List.of(
                "canara", "canbank", "canara bank", "cnrb"
        ));

        // Punjab National Bank
        BANK_IDENTIFIERS.put("PNB", List.of(
                "pnb", "punjab national", "punjab bank", "pnbsms"
        ));

        // Indian Bank
        BANK_IDENTIFIERS.put("INDIAN", List.of(
                "indian bank", "indianb", "indian"
        ));

        // Union Bank
        BANK_IDENTIFIERS.put("UNION", List.of(
                "union bank", "unionbank", "uboi", "ubi"
        ));

        // CITI Bank
        BANK_IDENTIFIERS.put("CITI", List.of(
                "citi", "citibank", "citi bank", "citiind"
        ));

        // Standard Chartered
        BANK_IDENTIFIERS.put("SC", List.of(
                "standard chartered", "sc bank", "scb", "scbnk"
        ));
    }

    private void initializeTransactionTypeKeywords() {
        // Debit indicators
        TRANSACTION_TYPE_KEYWORDS.put("DEBIT", List.of(
                "debited", "debit", "withdraw", "spent", "paid", "payment",
                "purchase", "shopping", "sent", "transferred out", "dr ",
                "pay", "paid to", "charged", "deducted", "purchase at", "trf to",
                "used at", "spent at", "cash withdrawal", "transacted", "processed"
        ));

        // Credit indicators
        TRANSACTION_TYPE_KEYWORDS.put("CREDIT", List.of(
                "credited", "credit", "received", "deposit", "refund", "cashback",
                "transferred in", "cr ", "added", "bonus", "reward", "reimbursement",
                "salary", "income", "paid to you", "trf from", "cash deposit", "return"
        ));
    }

    private void initializeAmountPatterns() {
        // Standard Indian currency patterns
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:Rs\\.?|INR|₹)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:Rs\\.?|INR|₹)"));

        // Patterns with amount indicators
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:amount|amt|sum of|txn amt|payment of)\\s*(?:Rs\\.?|INR|₹)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:amount|amt):?\\s*(?:Rs\\.?|INR|₹)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));

        // Patterns for "debited" or "credited" followed by amount
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:debited|credited|paid|spent)\\s*(?:with|for|by)?\\s*(?:Rs\\.?|INR|₹)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));

        // Patterns with decimals but no commas
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:Rs\\.?|INR|₹)\\s*(\\d+\\.\\d{1,2})"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(\\d+\\.\\d{1,2})\\s*(?:Rs\\.?|INR|₹)"));

        // Patterns without currency symbols
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)amount\\s*(?:is|of|:)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:txn|transaction|payment)\\s*(?:for|of|:)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));

        // More specific patterns for common bank formats
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)transaction of (?:Rs\\.?|INR|₹)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)paid (?:Rs\\.?|INR|₹)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
    }

    private void initializeDateFormats() {
        // Add various date formats used by banks
        DATE_FORMATS.add(new SimpleDateFormat("dd/MM/yy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd-MM-yy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd.MM.yy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd MMM yy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd-MMM-yy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("ddMMMyy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("ddMMMyyyy", Locale.getDefault()));
        DATE_FORMATS.add(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()));
    }

    private void initializeDatePatterns() {
        // Common date patterns found in SMS
        DATE_PATTERNS.add(Pattern.compile("(\\d{2}/\\d{2}/\\d{2,4})"));    // DD/MM/YY or DD/MM/YYYY
        DATE_PATTERNS.add(Pattern.compile("(\\d{2}-\\d{2}-\\d{2,4})"));    // DD-MM-YY or DD-MM-YYYY
        DATE_PATTERNS.add(Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{2,4})")); // DD.MM.YY or DD.MM.YYYY
        DATE_PATTERNS.add(Pattern.compile("(\\d{2}\\s+[A-Za-z]{3}\\s+\\d{2,4})"));  // DD MMM YY or DD MMM YYYY
        DATE_PATTERNS.add(Pattern.compile("(\\d{2}-[A-Za-z]{3}-\\d{2,4})"));  // DD-MMM-YY or DD-MMM-YYYY
        DATE_PATTERNS.add(Pattern.compile("(\\d{2}[A-Za-z]{3}\\d{2,4})"));    // DDMMMYY or DDMMMMYYYY

        // Patterns with date indicators
        DATE_PATTERNS.add(Pattern.compile("(?:on|dated|date:?)\\s+(\\d{2}/\\d{2}/\\d{2,4})"));
        DATE_PATTERNS.add(Pattern.compile("(?:on|dated|date:?)\\s+(\\d{2}-\\d{2}-\\d{2,4})"));
        DATE_PATTERNS.add(Pattern.compile("(?:on|dated|date:?)\\s+(\\d{2}\\s+[A-Za-z]{3}\\s+\\d{2,4})"));
        DATE_PATTERNS.add(Pattern.compile("(?:on|dated|date:?)\\s+(\\d{2}-[A-Za-z]{3}-\\d{2,4})"));

        // Patterns with month name
        DATE_PATTERNS.add(Pattern.compile("(?:on|dated|date:?)\\s+(\\d{1,2}(?:st|nd|rd|th)?\\s+[A-Za-z]+\\s+\\d{2,4})"));
    }

    private void initializeMerchantAndPurposeIndicators() {
        // Merchant indicators
        MERCHANT_INDICATORS.add("to ");
        MERCHANT_INDICATORS.add("at ");
        MERCHANT_INDICATORS.add("with ");
        MERCHANT_INDICATORS.add("merchant:");
        MERCHANT_INDICATORS.add("payee:");
        MERCHANT_INDICATORS.add("beneficiary:");
        MERCHANT_INDICATORS.add("to vpa ");
        MERCHANT_INDICATORS.add("upi-");
        MERCHANT_INDICATORS.add("from ");
        MERCHANT_INDICATORS.add("from a/c ");

        // Purpose indicators
        PURPOSE_INDICATORS.add("for ");
        PURPOSE_INDICATORS.add("towards ");
        PURPOSE_INDICATORS.add("purpose:");
        PURPOSE_INDICATORS.add("remarks:");
        PURPOSE_INDICATORS.add("ref:");
        PURPOSE_INDICATORS.add("info:");
    }

    private void initializeTransactionMethods() {
        // UPI
        TRANSACTION_METHODS.put("UPI", List.of(
                "upi", "upi ref", "upi id", "upi txn", "unified payment", "unified payments interface",
                "bhim", "gpay", "google pay", "phonepe", "paytm upi", "upid"
        ));

        // Card
        TRANSACTION_METHODS.put("CARD", List.of(
                "card", "credit card", "debit card", "card ending", "visa card", "mastercard",
                "rupay card", "card no:", "card payment", "credit", "debit", "axis card",
                "hdfc card", "sbi card", "icici card", "pos ", "card pos", "card purchase"
        ));

        // Net Banking
        TRANSACTION_METHODS.put("NETBANKING", List.of(
                "netbanking", "net banking", "internet banking", "online banking", "web banking",
                "ibanking", "i-banking", "bank transfer", "online transfer", "web transfer"
        ));

        // IMPS
        TRANSACTION_METHODS.put("IMPS", List.of(
                "imps", "immediate payment", "imps ref", "imps txn", "imps transfer"
        ));

        // NEFT
        TRANSACTION_METHODS.put("NEFT", List.of(
                "neft", "neft ref", "neft txn", "national electronic", "neft transfer"
        ));

        // RTGS
        TRANSACTION_METHODS.put("RTGS", List.of(
                "rtgs", "rtgs ref", "rtgs txn", "real time gross", "rtgs transfer"
        ));

        // ATM
        TRANSACTION_METHODS.put("ATM", List.of(
                "atm", "atm withdrawal", "cash withdrawal", "atm txn", "atm cash"
        ));

        // Cash
        TRANSACTION_METHODS.put("CASH", List.of(
                "cash deposit", "cash at bank", "bank cash", "branch deposit"
        ));

        // Cheque
        TRANSACTION_METHODS.put("CHEQUE", List.of(
                "cheque", "chq", "cheque no", "chq no", "cheque deposit", "clearing cheque"
        ));
    }

    private void initializeExclusionPatterns() {
        // Patterns for messages to exclude (not transactions)

        // OTP and verification messages
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(otp|one time password|verification code|secure code|security code)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(valid for|valid till|expires in|minutes|seconds)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(login|verification|authenticate|confirm your|authenticate your)"));

        // Promotional messages - now handled by isPromotionalMessage() for more intelligence
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(offer|discount|cashback|sale).*(avail|grab|limited time|last chance)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(apply now|register now|subscribe|limited period|hurry|special offer)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(exclusive|special).*(offer|deal|discount|rate|price)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(save up to|up to off|off on|% off|% discount)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(call now|buy now|shop now|download now|visit now|click now|click here)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(introducing|new launch|just arrived|latest|new offer|new product)"));

        // Future transactions and reminders
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(will be|shall be|upcoming|scheduled|pending).*(debited|credited|processed)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(payment due|due date|due on|please pay|kindly pay|reminder|please note|make a payment)"));

        // Account information and statements
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(statement|e-statement|estatement|account statement|mini statement).*(generated|available|ready)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(min amount due|total amount due|bill amount|bill generation|bill payment due)"));

        // Card related non-transaction messages
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(card).*(activated|dispatched|shipped|delivered|generated|blocked|unblocked)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(card).*(expire|expiry|expired|renew|renewed)"));

        // Other non-transaction notifications
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(password|credentials|user|username|userid|pin).*(changed|updated|reset)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(profile|details|information|address|email|mobile|phone).*(updated|changed|modified)"));

        // Balance/statement notifications without transaction information
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(available|avl|bal|balance).*(rs|inr)[^\\)]*$"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(bal|balance).*(inq|enquiry|inquiry)"));

        // Add more exclusion patterns for promotional content
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(exclusive|special).*(offer|deal|discount|opportunity)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(chance|opportunity).*(earn|get|receive|win)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(earn|get|receive).*(rewards|vouchers|cashback|discount)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(refer|recommend).*(friend|program)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(join|enroll).*(program|membership|club)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(hope you enjoyed|thank you for).*(shopping|experience|purchase)"));

        // Add patterns for balance-only messages
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(available|avl).*(bal|balance).*(as on|yesterday)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(cheques|checks).*(subject to clearing)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(real time|latest).*(bal|balance).*(dial|call|visit)"));

        // Add patterns for OTP messages
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(\\d{4,6}).*(otp|code|one.?time.?password)"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(otp|code|one.?time.?password).*(\\d{4,6})"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)(not done by you).*(call|contact)"));

        // Add specific patterns for the examples you provided
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)instant cash alert.*ready to be credited"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)best deal alert"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)get a loan of"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)check emi:"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)available bal.*as on yesterday"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)for real time a/c bal dial"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)cheques are subject to clearing"));

        // More specific patterns for promotional messages with URLs
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)hdfcbk\\.io"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)https://\\S+"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)http://\\S+"));

        // Patterns for T&C which indicate promotional content
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)T&C$"));
        EXCLUSION_PATTERNS.add(Pattern.compile("(?i)T&C apply"));
    }

    private void initializeCategoryKeywords() {
        // Food & Dining
        CATEGORY_KEYWORDS.put(Transaction.Categories.FOOD, List.of(
                "restaurant", "cafe", "coffee", "food", "dinner", "lunch", "breakfast",
                "swiggy", "zomato", "uber eat", "pizza", "burger", "grocery", "bigbasket",
                "supermarket", "hotel", "dining", "kitchen", "eatery", "dhaba",
                "bakery", "cake", "tea", "juice", "bar", "pub", "wine", "liquor",
                "mcdonalds", "kfc", "dominos", "subway", "starbucks", "costa"
        ));

        // Shopping
        CATEGORY_KEYWORDS.put(Transaction.Categories.SHOPPING, List.of(
                "shop", "store", "mall", "mart", "amazon", "flipkart", "myntra", "ajio",
                "retail", "purchase", "buy", "clothing", "fashion", "apparel", "marketplac",
                "electronics", "gadget", "furniture", "decor", "appliance", "lifestyle",
                "jewel", "accessory", "footwear", "shoe", "beauty", "cosmetic", "makeup",
                "online shopping", "e-commerce", "nykaa", "croma", "reliance digital", "tata cliq"
        ));

        // Bills & Utilities
        CATEGORY_KEYWORDS.put(Transaction.Categories.BILLS, List.of(
                "bill", "utility", "electric", "electricity", "water", "gas", "internet", "broadband",
                "wifi", "mobile", "phone", "recharge", "dth", "cable", "subscription", "renewal",
                "rent", "maintenance", "society", "apartment", "municipal", "tax", "insurance",
                "premium", "emi", "loan", "mortgage", "airtel", "jio", "vodafone", "bsnl", "tata sky",
                "dish tv", "sun direct", "lic", "policy", "bescom", "mseb", "payment"
        ));

        // Entertainment
        CATEGORY_KEYWORDS.put(Transaction.Categories.ENTERTAINMENT, List.of(
                "movie", "cinema", "theatre", "theater", "netflix", "amazon prime", "hotstar", "disney+",
                "sony liv", "zee5", "spotify", "gaana", "wynk", "music", "concert", "show", "event",
                "ticket", "bookmyshow", "game", "gaming", "entertainment", "play", "festival", "amusement",
                "park", "sport", "club", "pub", "casino", "bowling", "carnival", "circus", "zoo"
        ));

        // Transport
        CATEGORY_KEYWORDS.put(Transaction.Categories.TRANSPORT, List.of(
                "uber", "ola", "taxi", "cab", "auto", "bus", "train", "metro", "subway", "railway",
                "irctc", "flight", "airline", "air ticket", "travel", "transport", "petrol", "diesel",
                "fuel", "gas station", "parking", "toll", "indigo", "spicejet", "goair", "vistara",
                "air india", "makemytrip", "yatra", "via", "cleartrip", "easemytrip", "redbus"
        ));

        // Health
        CATEGORY_KEYWORDS.put(Transaction.Categories.HEALTH, List.of(
                "hospital", "clinic", "doctor", "medical", "pharmacy", "medicine", "health",
                "healthcare", "dental", "dentist", "eye", "optical", "therapy", "fitness", "gym",
                "wellness", "spa", "massage", "salon", "yoga", "meditation", "ayurveda", "homeopathy",
                "diagnostic", "lab", "test", "scan", "mri", "ct scan", "x-ray", "apollo", "medplus"
        ));

        // Education
        CATEGORY_KEYWORDS.put(Transaction.Categories.EDUCATION, List.of(
                "school", "college", "university", "education", "tuition", "class", "course",
                "tutorial", "coaching", "exam", "fee", "book", "library", "stationery", "supplies",
                "student", "scholarship", "campus", "academy", "institute", "training", "workshop",
                "seminar", "conference", "certification", "degree", "diploma", "phd", "research"
        ));
    }

    private void initializeBankMessageFormats() {
        // HDFC Bank formats
        List<Pattern> hdfcFormats = new ArrayList<>();
        hdfcFormats.add(Pattern.compile("(?i)HDFC Bank: Rs\\.?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(has been|is)\\s*(debited|credited)"));
        hdfcFormats.add(Pattern.compile("(?i)HDFC Bank(\\s+A/c.*?|:)\\s*(debited|credited|paid)\\s*(with|for)\\s*Rs\\.?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        hdfcFormats.add(Pattern.compile("(?i)Alert: Rs\\.(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?) debited from HDFC Bank"));
        BANK_MESSAGE_FORMATS.put("HDFC", hdfcFormats);

        // SBI formats
        List<Pattern> sbiFormats = new ArrayList<>();
        sbiFormats.add(Pattern.compile("(?i)(?:DEBIT|CREDIT|IMPS)\\s*(?:Alrt|Alert).*(A/c).*(?:Rs|INR)"));
        sbiFormats.add(Pattern.compile("(?i)(?:Rs|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:debited|credited|transferred)\\s*from.*SBI"));
        sbiFormats.add(Pattern.compile("(?i)SBI\\s*(?:A/C)*\\s*(?:No\\.?)*\\s*[X\\d]+\\s*(?:debited|credited)"));
        BANK_MESSAGE_FORMATS.put("SBI", sbiFormats);

        // ICICI formats
        List<Pattern> iciciFormats = new ArrayList<>();
        iciciFormats.add(Pattern.compile("(?i)ICICI Bank(?:: |\\s+)(?:INR|Rs\\.?)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:debited|credited)"));
        iciciFormats.add(Pattern.compile("(?i)ICICI(?:: |\\s+)(?:Txn|Transaction)\\s*(?:of|for)\\s*(?:INR|Rs\\.?)"));
        iciciFormats.add(Pattern.compile("(?i)Rs\\.?(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?) debited from A/C XX\\d+ ICICI"));
        BANK_MESSAGE_FORMATS.put("ICICI", iciciFormats);

        // AXIS formats
        List<Pattern> axisFormats = new ArrayList<>();
        axisFormats.add(Pattern.compile("(?i)(?:INR|Rs\\.?)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:debited|credited)\\s*from.*Axis"));
        axisFormats.add(Pattern.compile("(?i)Axis\\s+Bank(?:: |\\s+)(?:INR|Rs\\.?)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:spent|received)"));
        BANK_MESSAGE_FORMATS.put("AXIS", axisFormats);
    }

    /**
     * Quick check for obvious promotional content
     * Used as a final safety check for ambiguous messages
     * @param message The lowercase message content
     * @return true if the message looks promotional
     */
    private boolean looksLikePromotion(String message) {
        // Check for obvious promotional terms
        String[] obviousPromoTerms = {
                "offer", "discount", "sale", "promotion", "deal", "limited time",
                "special", "exclusive", "save", "buy", "free", "new launch",
                "introducing", "upgrade to", "apply now", "click here", "call now"
        };

        int count = 0;
        for (String term : obviousPromoTerms) {
            if (message.contains(term)) {
                count++;
                if (count >= 2) {
                    return true;  // If 2 or more promo terms are found
                }
            }
        }

        // Check for phrases that almost never appear in transaction messages
        if (message.contains("terms and conditions") ||
                message.contains("t&c apply") ||
                message.contains("visit our website") ||
                message.contains("visit store") ||
                message.contains("download our app") ||
                message.contains("best offer") ||
                message.contains("best price")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a message contains very strong evidence of being a transaction
     * These are patterns that are almost exclusively used in transaction messages
     *
     * @param message The lowercase message content
     * @return true if strong transaction evidence is found
     */
    private boolean hasStrongTransactionEvidence(String message) {
        // Very specific transaction patterns that almost never appear in promotional messages
        String[] strongPatterns = {
                "debited from a/c", "credited to a/c", "debited from your a/c", "credited to your a/c",
                "transaction completed", "transaction successful", "payment successful",
                "withdrawal from", "deposit to", "transaction of inr", "transaction of rs",
                "txn completed", "txn id:", "transaction id:", "transaction ref:", "utr:", "rrn:",
                "a/c no. xx", "acct xx", "xx123", "payment of rs", "payment of inr", "info: bal"
        };

        for (String pattern : strongPatterns) {
            if (message.contains(pattern)) {
                Log.d(TAG, "Strong transaction evidence found: " + pattern);
                return true;
            }
        }

        // Check for additional common transaction patterns
        boolean hasAccountRef = message.contains("a/c") || message.contains("account") ||
                message.contains("acct") || message.contains("acc no");

        boolean hasTransactionVerb = message.contains("debited") || message.contains("credited") ||
                message.contains("paid") || message.contains("withdrawn") ||
                message.contains("deposited") || message.contains("transferred");

        boolean hasAmount = message.matches(".*(?:rs\\.?|inr|₹)\\s*\\d+(?:,\\d+)*(?:\\.\\d{1,2})?.*");

        boolean hasReferenceNum = message.contains("ref:") || message.contains("ref no:") ||
                message.contains("ref #") || message.contains("txn id") ||
                message.contains("txn #");

        // If message has an account reference, transaction verb, amount and reference number,
        // it's very likely a transaction
        if (hasAccountRef && hasTransactionVerb && hasAmount) {
            if (hasReferenceNum) {
                Log.d(TAG, "Strong transaction evidence: has account, verb, amount and reference");
                return true;
            }

            Log.d(TAG, "Strong transaction evidence: has account, verb and amount");
            return true;
        }

        return false;
    }

    /**
     * Improved logic for isPromotionalMessage() method
     * to better detect promotional, OTP, and balance-only messages
     */
    private boolean isPromotionalMessage(String message) {
        int promotionalScore = 0;

        // Marketing call-to-action phrases (strong indicators)
        String[] callToActionPhrases = {
                "apply now", "avail now", "buy now", "call now", "click here", "download now",
                "grab the offer", "hurry", "limited offer", "limited period", "limited time",
                "offer valid till", "register now", "shop now", "subscribe now", "visit now",
                "visit our website", "visit store", "while stocks last", "t&c apply", "t&c",
                "terms and conditions", "terms & conditions", "refer a friend", "refer friends",
                "exclusive offer", "chance", "join our", "earn more", "earn vouchers", "more rewards"
        };

        // Marketing vocabulary (moderate indicators)
        String[] marketingTerms = {
                "absolutely free", "amazing", "best deal", "best offer", "best price", "big discount",
                "biggest", "bonus", "cashback", "coupon code", "deal", "discount", "exclusive",
                "extra", "fantastic", "free gift", "huge", "incredible", "lowest price", "off",
                "offer", "promo code", "promotion", "save", "special", "super offer", "unbelievable",
                "upgrade to", "use code", "win", "secure your", "reward", "secure", "earn", "program",
                "enjoyed your", "experience", "recommend", "voucher", "click to know more"
        };

        // Check for URLs - strong indicators of promotional content
        if (message.contains("http://") || message.contains("https://") ||
                message.contains("bit.ly") || message.contains(".io/") ||
                message.contains(".in/") || message.contains("www.")) {
            promotionalScore += 5;
            Log.d(TAG, "Promotional indicator found: URL");
        }

        // Check for call-to-action phrases (stronger indicators)
        for (String phrase : callToActionPhrases) {
            if (message.toLowerCase().contains(phrase)) {
                promotionalScore += 3;
                Log.d(TAG, "Promotional indicator found (CTA): " + phrase);
            }
        }

        // Check for marketing terms (moderate indicators)
        for (String term : marketingTerms) {
            if (message.toLowerCase().contains(term)) {
                promotionalScore += 2;
                Log.d(TAG, "Promotional indicator found (term): " + term);
            }
        }

        // Check if it's an OTP message
        if (message.contains("OTP") ||
                (message.matches(".*\\d{6}.*") && message.toLowerCase().contains("not share"))) {
            Log.d(TAG, "Message appears to be an OTP notification");
            return true;
        }

        // Check if it's a pure balance inquiry/statement without transaction
        if ((message.toLowerCase().contains("available bal") ||
                message.toLowerCase().contains("avl bal") ||
                message.toLowerCase().contains("balance in") ||
                message.toLowerCase().contains("bal in")) &&
                !message.toLowerCase().contains("debited") &&
                !message.toLowerCase().contains("credited") &&
                !message.toLowerCase().contains("payment") &&
                !message.toLowerCase().contains("transfer")) {

            Log.d(TAG, "Message appears to be a balance statement without transaction");
            return true;
        }

        // Check for standard transaction indicators that would reduce the likelihood
        // of the message being promotional
        if (message.toLowerCase().contains("debited from") ||
                message.toLowerCase().contains("credited to") ||
                message.toLowerCase().contains("transaction completed") ||
                message.toLowerCase().contains("payment successful") ||
                message.toLowerCase().contains("withdrawn from") ||
                (message.toLowerCase().contains("info:") && message.toLowerCase().contains("a/c"))) {
            promotionalScore -= 4;
            Log.d(TAG, "Transaction indicator found, reducing promotional score");
        }

        // Final analysis - higher threshold needed for strong transaction indicators
        int threshold = message.toLowerCase().contains("debited") ||
                message.toLowerCase().contains("credited") ? 5 : 3;

        Log.d(TAG, "Final promotional score: " + promotionalScore + " (threshold: " + threshold + ")");
        return promotionalScore >= threshold;
    }

    // ===== Main Parser Methods =====

    /**
     * Improved implementation of isLikelyTransactionMessage() to better filter out
     * non-transaction messages
     */
    public boolean isLikelyTransactionMessage(String message, String sender) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase();

        // Quick checks for definitely non-transaction messages

        // 1. URLs are strong indicators of promotional content
        if (message.contains("http://") || message.contains("https://") ||
                message.contains("bit.ly/") || message.contains(".io/") ||
                message.contains(".in/") || message.contains("www.")) {
            Log.d(TAG, "Message contains URL, likely promotional");
            return false;
        }

        // 2. OTP messages
        if (message.contains("OTP") ||
                (message.matches(".*\\d{6}.*") && lowerMessage.contains("not share"))) {
            Log.d(TAG, "Message appears to be an OTP notification");
            return false;
        }

        // Quick check for key phrases that indicate non-transactions
        if (lowerMessage.contains("alert!") &&
                (lowerMessage.contains("ready to be credited") || lowerMessage.contains("best deal"))) {
            Log.d(TAG, "Promotional alert detected, not a transaction");
            return false;
        }

        // Check for balance inquiry messages
        if (lowerMessage.contains("available bal") &&
                (lowerMessage.contains("as on yesterday") || lowerMessage.contains("subject to clearing"))) {
            Log.d(TAG, "Balance inquiry message detected, not a transaction");
            return false;
        }

        // Check for links/URLs - strong indicators of promotional content
        if (lowerMessage.contains("http") || lowerMessage.contains(".io/") ||
                lowerMessage.contains(".com/") || lowerMessage.contains("www.")) {
            Log.d(TAG, "URL detected in message, likely promotional");
            return false;
        }

        // Check for EMI/loan offers
        if ((lowerMessage.contains("loan") || lowerMessage.contains("emi")) &&
                (lowerMessage.contains("get") || lowerMessage.contains("offer") ||
                        lowerMessage.contains("check") || lowerMessage.contains("alert"))) {
            Log.d(TAG, "Loan/EMI offer detected, not a transaction");
            return false;
        }

        // 3. Balance-only messages
        boolean isBalanceOnly = (lowerMessage.contains("available bal") ||
                lowerMessage.contains("avl bal") ||
                lowerMessage.contains("balance in") ||
                lowerMessage.contains("bal in")) &&
                !lowerMessage.contains("debited") &&
                !lowerMessage.contains("credited") &&
                !lowerMessage.contains("payment") &&
                !lowerMessage.contains("transfer");

        if (isBalanceOnly) {
            Log.d(TAG, "Message appears to be a balance statement without transaction");
            return false;
        }

        // 4. "Terms and conditions" is almost always in promotional messages
        if (lowerMessage.contains("t&c apply") ||
                lowerMessage.contains("t&c") ||
                lowerMessage.contains("terms and conditions") ||
                lowerMessage.contains("terms & conditions")) {
            Log.d(TAG, "Message contains terms and conditions reference, likely promotional");
            return false;
        }

        // 5. Referral program messages
        if (lowerMessage.contains("refer a friend") ||
                lowerMessage.contains("refer friends") ||
                lowerMessage.contains("referral") ||
                lowerMessage.contains("earn more rewards")) {
            Log.d(TAG, "Message appears to be about a referral program");
            return false;
        }

        // Check if it matches any exclusion pattern
        for (Pattern pattern : EXCLUSION_PATTERNS) {
            if (pattern.matcher(lowerMessage).find()) {
                Log.d(TAG, "Message matches exclusion pattern: " + pattern.pattern());
                return false;
            }
        }

        // Enhanced promotional message filter
        if (isPromotionalMessage(lowerMessage)) {
            Log.d(TAG, "Message appears to be promotional, ignoring");
            return false;
        }

        // Enhanced promotional message filter
        if (isBalanceEnquiryMessage(lowerMessage)) {
            Log.d(TAG, "Message appears to be promotional, ignoring");
            return false;
        }

        // Transaction identification logic continues as before...
        // [rest of the existing method]

        // Check for strong transaction evidence first
        if (hasStrongTransactionEvidence(lowerMessage)) {
            return true;
        }

        // Must have both an amount AND one of these:
        // 1. Account reference (a/c, account)
        // 2. Clear debit/credit mention
        // 3. Transaction reference number

        boolean hasAmount = false;
        for (Pattern pattern : AMOUNT_PATTERNS) {
            if (pattern.matcher(message).find()) {
                hasAmount = true;
                break;
            }
        }

        if (!hasAmount) {
            Log.d(TAG, "No transaction amount found in message");
            return false;
        }

        boolean hasAccountRef = lowerMessage.contains("a/c") ||
                lowerMessage.contains("account") ||
                lowerMessage.contains("acct") ||
                lowerMessage.contains("ac no");

        boolean hasTransactionVerb = lowerMessage.contains("debited") ||
                lowerMessage.contains("credited") ||
                lowerMessage.contains("withdrawn") ||
                lowerMessage.contains("transferred");

        boolean hasTransactionRef = lowerMessage.contains("ref:") ||
                lowerMessage.contains("ref no:") ||
                lowerMessage.contains("txn id:") ||
                lowerMessage.contains("txn no:");

        // Only consider it a transaction if it has amount plus at least one other transaction indicator
        return hasAmount && (hasAccountRef || hasTransactionVerb || hasTransactionRef);
    }

    /**
     * Extra method to detect balance-only messages explicitly
     */
    private boolean isBalanceEnquiryMessage(String message) {
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase();

        // Check for balance-only indicators
        boolean hasBalanceIndicator = lowerMessage.contains("available bal") ||
                lowerMessage.contains("avl bal") ||
                lowerMessage.contains("balance in") ||
                lowerMessage.contains("bal in") ||
                lowerMessage.contains("current balance") ||
                lowerMessage.contains("account balance");

        boolean hasTransactionIndicator = lowerMessage.contains("debited") ||
                lowerMessage.contains("credited") ||
                lowerMessage.contains("payment") ||
                lowerMessage.contains("transfer");

        return hasBalanceIndicator && !hasTransactionIndicator;
    }

    /**
     * Parses an SMS message to extract transaction details
     *
     * @param message The SMS content to parse
     * @param sender The sender of the SMS (can be null)
     * @param timestamp The timestamp when the SMS was received
     * @return Transaction object if successfully parsed, null otherwise
     */
    public Transaction parseTransaction(String message, String sender, long timestamp) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        Log.d(TAG, "Starting parsing for message: " + message);

        // First, check if this is a transaction message
        if (!isLikelyTransactionMessage(message, sender)) {
            Log.d(TAG, "Message determined NOT to be a transaction message");
            return null;
        }

        // Extract transaction components
        String bank = identifyBank(message, sender);
        String type = determineTransactionType(message);
        Double amount = extractAmount(message);
        Long date = extractDate(message, timestamp);
        String merchantName = extractMerchant(message);
        String transactionMethod = determineTransactionMethod(message);
        String referenceNumber = extractReferenceNumber(message);
        String category = determineCategory(message, merchantName);

        // Log extracted components for debugging
        Log.d(TAG, "Extracted components: "
                + "\n Bank: " + bank
                + "\n Type: " + type
                + "\n Amount: " + amount
                + "\n Date: " + (date != null ? new Date(date) : "null")
                + "\n Merchant: " + merchantName
                + "\n Method: " + transactionMethod
                + "\n Reference: " + referenceNumber
                + "\n Category: " + category);

        // If we couldn't extract essential information, return null
        if (amount == null) {
            Log.d(TAG, "Failed to extract amount - skipping message");
            return null;
        }

        if (type == null) {
            // Default to DEBIT if we can't determine type (more common)
            type = "DEBIT";
            Log.d(TAG, "Transaction type not found, defaulting to DEBIT");
        }

        if (date == null) {
            // Use message timestamp if date not found
            date = timestamp;
            Log.d(TAG, "Transaction date not found, using SMS timestamp");
        }

        if (bank == null) {
            bank = "OTHER";
            Log.d(TAG, "Bank not identified, using 'OTHER'");
        }

        // Generate description
        String description = generateDescription(message, type, merchantName, transactionMethod, referenceNumber);

        // Create transaction object
        Transaction transaction = new Transaction(bank, type, amount, date, description);

        // Set additional properties
        transaction.setMerchantName(merchantName);
        transaction.setOriginalSms(message);

        if (category != null) {
            transaction.setCategory(category);
        }

        // Generate message hash for duplicate detection
        String messageHash = generateMessageHash(amount, date, description, merchantName);
        transaction.setMessageHash(messageHash);

        // Check for recurring transaction pattern
        boolean isRecurring = detectRecurringTransaction(message, description);
        transaction.setRecurring(isRecurring);

        Log.d(TAG, "Successfully parsed transaction: " + description);

        return transaction;
    }

    // ===== Component Extraction Methods =====

    /**
     * Identifies the bank from a message or sender
     */
    private String identifyBank(String message, String sender) {
        if (message == null) {
            return null;
        }

        String lowerMessage = message.toLowerCase();

        // First check sender ID if available
        if (sender != null && !sender.isEmpty()) {
            String lowerSender = sender.toLowerCase();
            for (Map.Entry<String, List<String>> entry : BANK_IDENTIFIERS.entrySet()) {
                for (String identifier : entry.getValue()) {
                    if (lowerSender.contains(identifier)) {
                        return entry.getKey();
                    }
                }
            }
        }

        // Then check message content
        for (Map.Entry<String, List<String>> entry : BANK_IDENTIFIERS.entrySet()) {
            for (String identifier : entry.getValue()) {
                if (lowerMessage.contains(identifier)) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    /**
     * Determines transaction type (DEBIT/CREDIT) from message
     */
    private String determineTransactionType(String message) {
        if (message == null) {
            return null;
        }

        String lowerMessage = message.toLowerCase();

        // Check for explicit debit indicators
        for (String keyword : TRANSACTION_TYPE_KEYWORDS.get("DEBIT")) {
            if (lowerMessage.contains(keyword)) {
                return "DEBIT";
            }
        }

        // Check for explicit credit indicators
        for (String keyword : TRANSACTION_TYPE_KEYWORDS.get("CREDIT")) {
            if (lowerMessage.contains(keyword)) {
                return "CREDIT";
            }
        }

        // If no explicit indicators, use contextual clues
        // Messages about spending or payment are usually debits
        if (lowerMessage.contains("spent") ||
                lowerMessage.contains("payment") ||
                lowerMessage.contains("paid") ||
                lowerMessage.contains("purchase")) {
            return "DEBIT";
        }

        // Messages about receiving money are usually credits
        if (lowerMessage.contains("received") ||
                lowerMessage.contains("credited") ||
                lowerMessage.contains("deposited") ||
                lowerMessage.contains("refund")) {
            return "CREDIT";
        }

        // Default to null if we can't determine
        return null;
    }

    /**
     * Extracts transaction amount from message
     */
    private Double extractAmount(String message) {
        if (message == null) {
            return null;
        }

        // Try all amount patterns
        for (Pattern pattern : AMOUNT_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                try {
                    // Remove commas before parsing
                    String amountStr = matcher.group(1).replace(",", "");
                    return Double.parseDouble(amountStr);
                } catch (NumberFormatException | IllegalStateException e) {
                    // Continue to next pattern if this fails
                    Log.d(TAG, "Failed to parse amount with pattern: " + pattern.pattern());
                }
            }
        }

        // Try a more general approach if specific patterns fail
        Pattern generalPattern = Pattern.compile("(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)");
        Matcher matcher = generalPattern.matcher(message);

        while (matcher.find()) {
            // Check if this number is likely an amount (nearby currency indicators)
            String context = getNearbyText(message, matcher.start(), 15);
            if (context.toLowerCase().contains("rs") ||
                    context.toLowerCase().contains("inr") ||
                    context.toLowerCase().contains("₹") ||
                    context.toLowerCase().contains("amount")) {

                try {
                    String amountStr = matcher.group(1).replace(",", "");
                    return Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    // Continue to next match
                }
            }
        }

        return null;
    }

    /**
     * Gets text surrounding a position in a string
     */
    private String getNearbyText(String text, int position, int radius) {
        int start = Math.max(0, position - radius);
        int end = Math.min(text.length(), position + radius);
        return text.substring(start, end);
    }

    /**
     * Extracts transaction date from message, falling back to provided timestamp
     */
    private Long extractDate(String message, long fallbackTimestamp) {
        if (message == null) {
            return fallbackTimestamp;
        }

        // Try to find dates using patterns
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String dateStr = matcher.group(1);
                // Try parsing with different date formats
                for (SimpleDateFormat format : DATE_FORMATS) {
                    try {
                        return format.parse(dateStr).getTime();
                    } catch (ParseException e) {
                        // Try next format
                    }
                }
            }
        }

        // Look for date context words like "on", "dated", etc.
        Pattern dateContextPattern = Pattern.compile("(?:on|dated)\\s+(\\d{1,2}[\\s-/\\.][A-Za-z]{3,9}[\\s-/\\.]\\d{2,4}|\\d{1,2}[\\s-/\\.]\\d{1,2}[\\s-/\\.]\\d{2,4})");
        Matcher contextMatcher = dateContextPattern.matcher(message);

        if (contextMatcher.find()) {
            String dateStr = contextMatcher.group(1);
            // Try parsing with different date formats
            for (SimpleDateFormat format : DATE_FORMATS) {
                try {
                    return format.parse(dateStr).getTime();
                } catch (ParseException e) {
                    // Try next format
                }
            }
        }

        // If no date found, use fallback
        return fallbackTimestamp;
    }

    /**
     * Extracts merchant name from transaction message
     */
    private String extractMerchant(String message) {
        if (message == null) {
            return null;
        }

        String lowerMessage = message.toLowerCase();

        // Try to find merchant based on common indicators
        for (String indicator : MERCHANT_INDICATORS) {
            int index = lowerMessage.indexOf(indicator);
            if (index >= 0) {
                // Extract text after the indicator
                String afterIndicator = lowerMessage.substring(index + indicator.length());

                // Get first few words (likely the merchant name)
                String[] words = afterIndicator.split("\\s+");
                StringBuilder merchant = new StringBuilder();

                // Take up to 4 words as the merchant name
                int wordLimit = Math.min(4, words.length);
                for (int i = 0; i < wordLimit; i++) {
                    // Stop on common end markers
                    if (words[i].matches("on|info|alert|dated|ref|id|upi|rs|inr")) break;

                    if (merchant.length() > 0) merchant.append(" ");
                    merchant.append(words[i]);

                    // Stop at punctuation
                    if (words[i].endsWith(".") || words[i].endsWith(",") ||
                            words[i].endsWith(";") || words[i].endsWith(":")) {
                        merchant = new StringBuilder(merchant.substring(0, merchant.length() - 1));
                        break;
                    }
                }

                String result = merchant.toString().trim();
                if (!result.isEmpty()) {
                    return cleanMerchantName(result);
                }
            }
        }

        // Check for UPI transaction with VPA (Virtual Payment Address)
        Pattern upiPattern = Pattern.compile("(?i)(?:upi|vpa)[\\s:-]([^\\s;.,]+@[^\\s;.,]+)");
        Matcher upiMatcher = upiPattern.matcher(message);
        if (upiMatcher.find()) {
            return upiMatcher.group(1);
        }

        // Check for merchant name in card transaction
        Pattern cardPattern = Pattern.compile("(?i)(?:card\\s+used\\s+at|purchase\\s+at|spent\\s+at)\\s+([A-Za-z0-9\\s&.,'\\-]+?)(?=\\s+on|\\s+info|\\s+[0-9]|$)");
        Matcher cardMatcher = cardPattern.matcher(message);
        if (cardMatcher.find()) {
            return cleanMerchantName(cardMatcher.group(1));
        }

        return null;
    }

    /**
     * Cleans up extracted merchant name
     */
    private String cleanMerchantName(String merchant) {
        if (merchant == null) {
            return null;
        }

        // Remove any leading/trailing punctuation and spaces
        merchant = merchant.replaceAll("^[\\s,.;:'`\"]+", "").replaceAll("[\\s,.;:'`\"]+$", "");

        // Skip very short strings or common words
        if (merchant.length() < 2 ||
                merchant.equalsIgnoreCase("of") ||
                merchant.equalsIgnoreCase("to") ||
                merchant.equalsIgnoreCase("at") ||
                merchant.equalsIgnoreCase("for")) {
            return null;
        }

        // Filter out transaction types (often misrecognized as merchants)
        String[] transactionTypes = {"upi", "neft", "imps", "rtgs", "atm", "pos", "emi"};
        for (String type : transactionTypes) {
            if (merchant.equalsIgnoreCase(type)) {
                return null;
            }
        }

        // Capitalize first letter of each word
        String[] words = merchant.split("\\s+");
        StringBuilder capitalizedMerchant = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                if (capitalizedMerchant.length() > 0) {
                    capitalizedMerchant.append(" ");
                }

                if (word.length() == 1) {
                    capitalizedMerchant.append(word.toUpperCase());
                } else {
                    capitalizedMerchant.append(Character.toUpperCase(word.charAt(0)))
                            .append(word.substring(1).toLowerCase());
                }
            }
        }

        return capitalizedMerchant.toString();
    }

    /**
     * Determines the transaction method (UPI, NEFT, Card, etc.)
     */
    private String determineTransactionMethod(String message) {
        if (message == null) {
            return "Transaction";
        }

        String lowerMessage = message.toLowerCase();

        // Check for transaction method keywords
        for (Map.Entry<String, List<String>> entry : TRANSACTION_METHODS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerMessage.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        // Default to "Transaction" if no specific method found
        return "Transaction";
    }

    /**
     * Extracts reference number from message
     */
    private String extractReferenceNumber(String message) {
        if (message == null) {
            return null;
        }

        // Common reference patterns
        Pattern[] refPatterns = {
                Pattern.compile("(?i)(?:ref|reference)\\s*(?:no|number|#)?\\s*[:.=]?\\s*([A-Za-z0-9]+)"),
                Pattern.compile("(?i)(?:txn|transaction)\\s*(?:id|no|number|#)?\\s*[:.=]?\\s*([A-Za-z0-9]+)"),
                Pattern.compile("(?i)(?:utr|rrn)\\s*[:.=]?\\s*([A-Za-z0-9]+)"),
                Pattern.compile("(?i)(?:imps|neft|rtgs)\\s*(?:ref|id)?\\s*[:.=]?\\s*([A-Za-z0-9]+)")
        };

        for (Pattern pattern : refPatterns) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    /**
     * Determines transaction category based on message content and merchant
     */
    private String determineCategory(String message, String merchantName) {
        if (message == null) {
            return null;
        }

        String lowerMessage = message.toLowerCase();
        String combinedText = lowerMessage;

        if (merchantName != null) {
            combinedText += " " + merchantName.toLowerCase();
        }

        // Check for category keywords
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (combinedText.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        // Check for transaction method hints
        if (combinedText.contains("upi")) {
            // UPI payments could be anything, default to Shopping
            return Transaction.Categories.SHOPPING;
        } else if (combinedText.contains("atm") || combinedText.contains("cash withdrawal")) {
            // ATM withdrawals are usually for miscellaneous expenses
            return Transaction.Categories.OTHERS;
        } else if (combinedText.contains("bill") || combinedText.contains("recharge")) {
            // Bill payments and recharges
            return Transaction.Categories.BILLS;
        }

        // Default to OTHERS if no category found
        return Transaction.Categories.OTHERS;
    }

    /**
     * Generates a transaction description from the extracted components
     */
    private String generateDescription(String message, String type, String merchantName,
                                       String transactionMethod, String referenceNumber) {
        StringBuilder description = new StringBuilder();

        // Start with transaction method
        description.append(transactionMethod);

        // Add transaction type
        if ("DEBIT".equals(type)) {
            description.append(" payment");
        } else {
            description.append(" received");
        }

        // Add merchant if available
        if (merchantName != null && !merchantName.isEmpty()) {
            if ("DEBIT".equals(type)) {
                description.append(" to ");
            } else {
                description.append(" from ");
            }
            description.append(merchantName);
        }

        // Add reference number if available
        if (referenceNumber != null && !referenceNumber.isEmpty()) {
            description.append(" (Ref: ").append(referenceNumber).append(")");
        }

        // Extract purpose if available
        String purpose = extractPurpose(message);
        if (purpose != null && !purpose.isEmpty()) {
            description.append(" for ").append(purpose);
        }

        // Check for special transaction types
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("refund")) {
            description.append(" (Refund)");
        } else if (lowerMessage.contains("cashback")) {
            description.append(" (Cashback)");
        } else if (lowerMessage.contains("salary"))
            // Check for special transaction types
            lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("refund")) {
            description.append(" (Refund)");
        } else if (lowerMessage.contains("cashback")) {
            description.append(" (Cashback)");
        } else if (lowerMessage.contains("salary")) {
            description.append(" (Salary)");
        } else if (lowerMessage.contains("emi") || lowerMessage.contains("loan")) {
            description.append(" (EMI Payment)");
        }

        return description.toString();
    }

    /**
     * Extracts the purpose of the transaction
     */
    private String extractPurpose(String message) {
        if (message == null) {
            return null;
        }

        String lowerMessage = message.toLowerCase();

        // Try to find purpose based on common indicators
        for (String indicator : PURPOSE_INDICATORS) {
            int index = lowerMessage.indexOf(indicator);
            if (index >= 0) {
                // Extract text after the indicator
                String afterIndicator = lowerMessage.substring(index + indicator.length());

                // Get first few words (likely the purpose)
                String[] words = afterIndicator.split("\\s+");
                StringBuilder purpose = new StringBuilder();

                // Take up to 3 words as the purpose
                int wordLimit = Math.min(3, words.length);
                for (int i = 0; i < wordLimit; i++) {
                    // Stop on common end markers
                    if (words[i].matches("on|info|alert|dated|ref|id|upi|rs|inr")) break;

                    if (purpose.length() > 0) purpose.append(" ");
                    purpose.append(words[i]);

                    // Stop at punctuation
                    if (words[i].endsWith(".") || words[i].endsWith(",") ||
                            words[i].endsWith(";") || words[i].endsWith(":")) {
                        purpose = new StringBuilder(purpose.substring(0, purpose.length() - 1));
                        break;
                    }
                }

                String result = purpose.toString().trim();
                if (!result.isEmpty()) {
                    return cleanPurposeText(result);
                }
            }
        }

        return null;
    }

    /**
     * Cleans up extracted purpose text
     */
    private String cleanPurposeText(String purpose) {
        if (purpose == null) {
            return null;
        }

        // Remove any leading/trailing punctuation and spaces
        purpose = purpose.replaceAll("^[\\s,.;:'`\"]+", "").replaceAll("[\\s,.;:'`\"]+$", "");

        // Skip very short strings or common words
        if (purpose.length() < 2 ||
                purpose.equalsIgnoreCase("of") ||
                purpose.equalsIgnoreCase("to") ||
                purpose.equalsIgnoreCase("at") ||
                purpose.equalsIgnoreCase("on")) {
            return null;
        }

        // Capitalize first letter
        if (purpose.length() > 1) {
            purpose = Character.toUpperCase(purpose.charAt(0)) + purpose.substring(1).toLowerCase();
        }

        return purpose;
    }

    /**
     * Detects if a transaction is likely recurring based on message content
     */
    private boolean detectRecurringTransaction(String message, String description) {
        if (message == null) {
            return false;
        }

        String lowerMessage = message.toLowerCase();

        // Look for recurring transaction indicators
        String[] recurringIndicators = {
                "emi", "monthly", "subscription", "recurring", "auto-debit", "auto debit",
                "standing instruction", "si debit", "automatic payment", "auto payment"
        };

        for (String indicator : recurringIndicators) {
            if (lowerMessage.contains(indicator)) {
                return true;
            }
        }

        // Check description for common recurring payment merchants
        String lowerDescription = description.toLowerCase();
        String[] recurringMerchants = {
                "netflix", "amazon prime", "hotstar", "spotify", "apple", "google play",
                "insurance", "rent", "electricity", "water", "gas", "internet", "broadband",
                "mobile", "phone", "dth", "cable", "loan", "emi", "mortgage"
        };

        for (String merchant : recurringMerchants) {
            if (lowerDescription.contains(merchant)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates a unique hash for the transaction to prevent duplicates
     */
    private String generateMessageHash(Double amount, Long date, String description, String merchantName) {
        // Format amount with 2 decimal places
        String amountStr = String.format("%.2f", amount);

        // Use day-level precision for date to handle minor time differences
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        String dateStr = String.valueOf(cal.getTimeInMillis());

        // Merchant info (if available)
        String merchantInfo = (merchantName != null && !merchantName.isEmpty()) ?
                merchantName.toLowerCase() : "";

        // Create a unique fingerprint
        String content = amountStr + dateStr + description + merchantInfo;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error generating hash", e);
            return content;
        }
    }

    /**
     * Checks if a transaction with the same hash already exists
     */
    public boolean isDuplicate(Transaction transaction, TransactionDao dao) {
        if (transaction == null || dao == null) {
            return false;
        }

        // Check if this transaction's hash already exists in the database
        String hash = transaction.getMessageHash();
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        return dao.hasTransaction(hash);
    }

    /**
     * Utility method to try to extract any valid transaction information from a problematic message
     * This is a fallback method when the primary parsing fails
     */
    public Transaction attemptFallbackParsing(String message, String sender, long timestamp) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        Log.d(TAG, "Attempting fallback parsing for message: " + message);

        // Just extract basic amount and assume a debit transaction
        Double amount = extractAmount(message);
        if (amount == null) {
            Log.d(TAG, "Fallback parsing failed - no amount found");
            return null;
        }

        // Try to determine transaction type, default to DEBIT
        String type = determineTransactionType(message);
        if (type == null) {
            type = "DEBIT";
        }

        // Try to identify bank
        String bank = identifyBank(message, sender);
        if (bank == null) {
            bank = "OTHER";
        }

        // Try to extract merchant
        String merchantName = extractMerchant(message);

        // Create simple description
        String description = "Transaction " + (type.equals("DEBIT") ? "payment" : "received");
        if (merchantName != null && !merchantName.isEmpty()) {
            description += type.equals("DEBIT") ? " to " : " from ";
            description += merchantName;
        }

        // Create transaction with minimal info
        Transaction transaction = new Transaction(bank, type, amount, timestamp, description);
        transaction.setMerchantName(merchantName);
        transaction.setOriginalSms(message);

        // Generate hash
        String messageHash = generateMessageHash(amount, timestamp, description, merchantName);
        transaction.setMessageHash(messageHash);

        Log.d(TAG, "Fallback parsing produced transaction: " + description);

        return transaction;
    }
}