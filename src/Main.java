import com.denkbares.plugin.JPFPluginManager;
import com.denkbares.strings.Strings;
import de.d3web.core.io.PersistenceManager;
import de.d3web.core.knowledge.KnowledgeBase;
import de.d3web.core.knowledge.terminology.Choice;
import de.d3web.core.knowledge.terminology.Question;
import de.d3web.core.knowledge.terminology.QuestionChoice;
import de.d3web.core.knowledge.terminology.QuestionNum;
import de.d3web.core.knowledge.terminology.info.BasicProperties;
import de.d3web.core.knowledge.terminology.info.MMInfo;
import de.d3web.core.knowledge.terminology.info.NumericalInterval;
import de.d3web.core.records.SessionConversionFactory;
import de.d3web.core.records.io.SessionPersistenceManager;
import de.d3web.core.session.QuestionValue;
import de.d3web.core.session.Session;
import de.d3web.core.session.SessionFactory;
import de.d3web.core.session.ValueUtils;
import de.d3web.core.session.blackboard.FactFactory;
import de.d3web.core.session.values.ChoiceValue;
import de.d3web.core.session.values.NumValue;
import de.d3web.core.session.values.Unknown;
import de.d3web.interview.Form;
import de.d3web.interview.Interview;
import de.d3web.interview.inference.PSMethodInterview;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class Main {

    public static String D3WEB = "../d3web-KnowWE-distribution-12.9-SNAPSHOT/d3web/lib";
    public static String KB = "./KB/AW4.0.d3web";
    public static String SESSION_RES = "./KB/session_res.xml";

    /**
     * Demo interview session based on the provided KB.
     *
     * @throws IOException
     */
    public static void demo() throws IOException {

        // init persistence plugins
        JPFPluginManager.init(D3WEB);

        // load KB
        PersistenceManager persistenceManager = PersistenceManager.getInstance();
        KnowledgeBase knowledgeBase = persistenceManager.load(new File(KB));

        // launch interview session
        Session session = SessionFactory.createSession(knowledgeBase);
        Interview interview = session.getSessionObject(session.getPSMethodInstance(PSMethodInterview.class));
        Form form;

        while ((form = interview.nextForm()) != null && !form.isEmpty()) {
            // System.out.println("starting next interview form..");

            for (int i = 0; i < form.getActiveQuestions().size(); i++) {
                Question question = form.getActiveQuestions().get(i);
                System.out.println("############### QUESTION: " + question.getName());

                boolean unknown = BasicProperties.isUnknownVisible(question);
                QuestionValue value = Unknown.getInstance();

                if (question instanceof QuestionChoice) {
                    value = processChoices(question, unknown);
                } else if (question instanceof QuestionNum) {
                    NumericalInterval range = question.getInfoStore().getValue(BasicProperties.QUESTION_NUM_RANGE);
                    value = new NumValue(readNumValue(range));
                }

                // answer question
                session.getBlackboard().addValueFact(FactFactory.createUserEnteredFact(question, value));
                System.out.println("--> " + ValueUtils.getVerbalization(question, value, Locale.ROOT));
            }
        }
        saveToFile(session);
    }

    /**
     * Processes the specified question's choices.
     *
     * @param question - question to process choices for
     * @param unknown  - whether 'unknown' is an option
     * @return option (choice) entered by the user
     * @throws IOException
     */
    private static QuestionValue processChoices(Question question, boolean unknown) throws IOException {
        List<Choice> choices = ((QuestionChoice) question).getAllAlternatives();
        if (unknown) {
            System.out.print("[0] unknown  ");
        }
        for (int choice = 0; choice < choices.size(); choice++) {
            System.out.print("[" + (choice + 1) + "] "
                    + Strings.htmlToPlain(MMInfo.getPrompt(choices.get(choice), Locale.ROOT)) + "  ");
        }
        // prompt value
        int idx = unknown || choices.isEmpty() ? 0 : readIdx(choices.size());
        if (idx >= 1) {
            return new ChoiceValue(choices.get(idx - 1));
        }
        return null;
    }

    /**
     * Saves the session to file.
     *
     * @param session - session to be saved
     * @throws IOException
     */
    private static void saveToFile(Session session) throws IOException {
        OutputStream out = new FileOutputStream(SESSION_RES);
        SessionPersistenceManager.getInstance().saveSessions(out, SessionConversionFactory.copyToSessionRecord(session));
        out.flush();
        out.close();
    }

    /**
     * Reads a numerical value from the user.
     *
     * @param range - range to read value form
     * @return read value
     * @throws IOException
     */
    private static double readNumValue(NumericalInterval range) throws IOException {
        String line = "";
        while (true) {
            try {
                BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                line = keyboard.readLine();
                double number = Double.parseDouble(line);
                if (range != null
                        && (range.isLeftOpen() && number <= range.getLeft()
                        || !range.isLeftOpen() && number < range.getLeft()
                        || range.isRightOpen() && number >= range.getRight()
                        || !range.isRightOpen() && number > range.getRight())) {
                    System.out.println();
                    System.out.print("please enter a value in the following range: " + range + "  ");
                } else {
                    return number;
                }
            } catch (NumberFormatException ignore) {
                System.out.println("unable to parse number: '" + line + "'");
            }
        }
    }

    /**
     * Reads the choice index from the user.
     *
     * @param numOfChoices - number of choices
     * @return entered index
     * @throws IOException
     */
    private static int readIdx(int numOfChoices) throws IOException {
        while (true) {
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = keyboard.readLine();
            int idx = Integer.parseInt(line.trim());
            if (idx >= 0 && idx <= numOfChoices) {
                return idx;
            } else {
                System.out.println("entered infeasible index: " + idx);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("test");
        try {
            demo();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
