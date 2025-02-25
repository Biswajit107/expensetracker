package com.example.expensetracker.nlp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BankPatternDatabase {
    private final Map<String, BankPatterns> bankPatternsMap = new HashMap<>();

    public BankPatternDatabase() {
        initializePatterns();
    }

    private void initializePatterns() {
        // HDFC Bank
        BankPatterns hdfc = new BankPatterns();
        hdfc.addIdentificationPattern(Pattern.compile("HDFC(BK)?|HD\\s?FC", Pattern.CASE_INSENSITIVE));
        hdfc.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s+has been (debited|credited)"));
        hdfc.addTransactionPattern(Pattern.compile("(?:debited|credited) from your.*(?:a/c|account)", Pattern.CASE_INSENSITIVE));
        hdfc.addTransactionPattern(Pattern.compile("Info: (?:INR|Rs).*(?:debited|credited|spent)", Pattern.CASE_INSENSITIVE));
        hdfc.addTransactionPattern(Pattern.compile("(?:payment|purchase|transfer).*(?:made|done|completed)", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("HDFC", hdfc);

        // SBI Bank
        BankPatterns sbi = new BankPatterns();
        sbi.addIdentificationPattern(Pattern.compile("SBI(INB)?|State Bank", Pattern.CASE_INSENSITIVE));
        sbi.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s+(?:debited|credited)"));
        sbi.addTransactionPattern(Pattern.compile("(?:debited|credited) from your account", Pattern.CASE_INSENSITIVE));
        sbi.addTransactionPattern(Pattern.compile("DEBIT.*A/c no", Pattern.CASE_INSENSITIVE));
        sbi.addTransactionPattern(Pattern.compile("CREDIT.*A/c no", Pattern.CASE_INSENSITIVE));
        sbi.addTransactionPattern(Pattern.compile("withdrawn.*ATM|POS", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("SBI", sbi);

        // ICICI Bank
        BankPatterns icici = new BankPatterns();
        icici.addIdentificationPattern(Pattern.compile("ICICI(B)?|I-Mobile", Pattern.CASE_INSENSITIVE));
        icici.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s+has been"));
        icici.addTransactionPattern(Pattern.compile("(?:debited|credited) from your ICICI Bank", Pattern.CASE_INSENSITIVE));
        icici.addTransactionPattern(Pattern.compile("Transaction of INR.*(?:done|completed)", Pattern.CASE_INSENSITIVE));
        icici.addTransactionPattern(Pattern.compile("Txn of (?:Rs|INR).*(?:made|processed)", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("ICICI", icici);

        // Axis Bank
        BankPatterns axis = new BankPatterns();
        axis.addIdentificationPattern(Pattern.compile("AXIS(BK)?", Pattern.CASE_INSENSITIVE));
        axis.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s+(?:debited|credited)"));
        axis.addTransactionPattern(Pattern.compile("Your Axis Bank .* has been debited", Pattern.CASE_INSENSITIVE));
        axis.addTransactionPattern(Pattern.compile("Your Axis Bank .* has been credited", Pattern.CASE_INSENSITIVE));
        axis.addTransactionPattern(Pattern.compile("(?:UPI|NEFT|IMPS|RTGS) transaction", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("AXIS", axis);

        // Kotak Bank
        BankPatterns kotak = new BankPatterns();
        kotak.addIdentificationPattern(Pattern.compile("KOTAK(B)?", Pattern.CASE_INSENSITIVE));
        kotak.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        kotak.addTransactionPattern(Pattern.compile("Money transferred|Payment made", Pattern.CASE_INSENSITIVE));
        kotak.addTransactionPattern(Pattern.compile("has been debited from your account", Pattern.CASE_INSENSITIVE));
        kotak.addTransactionPattern(Pattern.compile("has been credited to your account", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("KOTAK", kotak);

        // Yes Bank
        BankPatterns yes = new BankPatterns();
        yes.addIdentificationPattern(Pattern.compile("YES(BNK)?", Pattern.CASE_INSENSITIVE));
        yes.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        yes.addTransactionPattern(Pattern.compile("Your YES BANK .* has been debited", Pattern.CASE_INSENSITIVE));
        yes.addTransactionPattern(Pattern.compile("Your YES BANK .* has been credited", Pattern.CASE_INSENSITIVE));
        yes.addTransactionPattern(Pattern.compile("(?:UPI|NEFT|IMPS|RTGS) transaction", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("YES", yes);

        // Bank of India
        BankPatterns boi = new BankPatterns();
        boi.addIdentificationPattern(Pattern.compile("BOI(IND)?", Pattern.CASE_INSENSITIVE));
        boi.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        boi.addTransactionPattern(Pattern.compile("debited for", Pattern.CASE_INSENSITIVE));
        boi.addTransactionPattern(Pattern.compile("credited with", Pattern.CASE_INSENSITIVE));
        boi.addTransactionPattern(Pattern.compile("Transaction of Rs", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("BOI", boi);

        // Punjab National Bank
        BankPatterns pnb = new BankPatterns();
        pnb.addIdentificationPattern(Pattern.compile("PNB(SMS)?", Pattern.CASE_INSENSITIVE));
        pnb.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        pnb.addTransactionPattern(Pattern.compile("debited from your account", Pattern.CASE_INSENSITIVE));
        pnb.addTransactionPattern(Pattern.compile("credited to your account", Pattern.CASE_INSENSITIVE));
        pnb.addTransactionPattern(Pattern.compile("Transaction of Rs", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("PNB", pnb);

        // Canara Bank
        BankPatterns canara = new BankPatterns();
        canara.addIdentificationPattern(Pattern.compile("CANBNK|Canara", Pattern.CASE_INSENSITIVE));
        canara.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        canara.addTransactionPattern(Pattern.compile("Your a/c is debited", Pattern.CASE_INSENSITIVE));
        canara.addTransactionPattern(Pattern.compile("Your a/c is credited", Pattern.CASE_INSENSITIVE));
        canara.addTransactionPattern(Pattern.compile("transaction at", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("CANARA", canara);

        // Bank of Baroda
        BankPatterns bob = new BankPatterns();
        bob.addIdentificationPattern(Pattern.compile("BOB(TXN)?", Pattern.CASE_INSENSITIVE));
        bob.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        bob.addTransactionPattern(Pattern.compile("debited from your", Pattern.CASE_INSENSITIVE));
        bob.addTransactionPattern(Pattern.compile("credited to your", Pattern.CASE_INSENSITIVE));
        bob.addTransactionPattern(Pattern.compile("transaction for", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("BOB", bob);

        // IDBI Bank
        BankPatterns idbi = new BankPatterns();
        idbi.addIdentificationPattern(Pattern.compile("IDBI(BK)?", Pattern.CASE_INSENSITIVE));
        idbi.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        idbi.addTransactionPattern(Pattern.compile("debited from your", Pattern.CASE_INSENSITIVE));
        idbi.addTransactionPattern(Pattern.compile("credited to your", Pattern.CASE_INSENSITIVE));
        idbi.addTransactionPattern(Pattern.compile("Payment of", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("IDBI", idbi);

        // General patterns (for unidentified bank messages)
        BankPatterns general = new BankPatterns();
        general.addAmountPattern(Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        general.addTransactionPattern(Pattern.compile("has been (?:debited|credited)", Pattern.CASE_INSENSITIVE));
        general.addTransactionPattern(Pattern.compile("(?:debited|credited) (?:from|to) your account", Pattern.CASE_INSENSITIVE));
        general.addTransactionPattern(Pattern.compile("(?:UPI|NEFT|IMPS|RTGS) transaction", Pattern.CASE_INSENSITIVE));
        general.addTransactionPattern(Pattern.compile("(?:payment|purchase) (?:of|for) (?:Rs\\.?|INR)", Pattern.CASE_INSENSITIVE));
        bankPatternsMap.put("GENERAL", general);
    }

    public BankPatterns getPatternsForBank(String bankName) {
        return bankPatternsMap.get(bankName);
    }

    public String identifyBank(String sender, String message) {
        if (sender != null) {
            for (Map.Entry<String, BankPatterns> entry : bankPatternsMap.entrySet()) {
                for (Pattern pattern : entry.getValue().getIdentificationPatterns()) {
                    if (pattern.matcher(sender).find()) {
                        return entry.getKey();
                    }
                }
            }
        }

        // If no match by sender, try message content
        if (message != null) {
            for (Map.Entry<String, BankPatterns> entry : bankPatternsMap.entrySet()) {
                for (Pattern pattern : entry.getValue().getIdentificationPatterns()) {
                    if (pattern.matcher(message).find()) {
                        return entry.getKey();
                    }
                }
            }
        }

        return "GENERAL";
    }

    public static class BankPatterns {
        private final List<Pattern> identificationPatterns = new ArrayList<>();
        private final List<Pattern> amountPatterns = new ArrayList<>();
        private final List<Pattern> transactionPatterns = new ArrayList<>();

        public void addIdentificationPattern(Pattern pattern) {
            identificationPatterns.add(pattern);
        }

        public void addAmountPattern(Pattern pattern) {
            amountPatterns.add(pattern);
        }

        public void addTransactionPattern(Pattern pattern) {
            transactionPatterns.add(pattern);
        }

        public List<Pattern> getIdentificationPatterns() {
            return identificationPatterns;
        }

        public List<Pattern> getAmountPatterns() {
            return amountPatterns;
        }

        public List<Pattern> getTransactionPatterns() {
            return transactionPatterns;
        }

        public boolean hasTransactionPattern() {
            return !transactionPatterns.isEmpty();
        }
    }

}