import com.denkbares.plugin.JPFPluginManager;
import com.denkbares.strings.Strings;
import de.d3web.core.io.PersistenceManager;
import de.d3web.core.knowledge.KnowledgeBase;
import de.d3web.core.knowledge.terminology.*;
import de.d3web.core.knowledge.terminology.info.BasicProperties;
import de.d3web.core.knowledge.terminology.info.MMInfo;
import de.d3web.core.records.SessionConversionFactory;
import de.d3web.core.records.io.SessionPersistenceManager;
import de.d3web.core.session.QuestionValue;
import de.d3web.core.session.Session;
import de.d3web.core.session.SessionFactory;
import de.d3web.core.session.ValueUtils;
import de.d3web.core.session.blackboard.FactFactory;
import de.d3web.core.session.values.ChoiceValue;
import de.d3web.core.session.values.Unknown;
import de.d3web.interview.Form;
import de.d3web.interview.Interview;
import de.d3web.interview.inference.PSMethodInterview;
import py4j.GatewayServer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Main {

    public static final String D3WEB = "../d3web-KnowWE-distribution-12.9-SNAPSHOT/d3web/lib";
    public static final String KB = "./res/CustomerXPS.d3web";

    /**
     * Demo interview session based on the provided KB.
     *
     * @param sessionPath - directory path to save session to
     * @return XPS solution (initial diagnosis)
     * @throws IOException
     */
    public static String demo(String sessionPath) throws IOException {

        // init persistence plugins
        JPFPluginManager.init(D3WEB);

        // load KB
        PersistenceManager persistenceManager = PersistenceManager.getInstance();
        KnowledgeBase knowledgeBase = persistenceManager.load(new File(KB));

        // launch interview session
        Session session = SessionFactory.createSession(knowledgeBase);
        Interview interview = session.getSessionObject(session.getPSMethodInstance(PSMethodInterview.class));
        Form form;
        String solution = "";

        while ((form = interview.nextForm()) != null && !form.isEmpty()) {

            for (int i = 0; i < form.getActiveQuestions().size(); i++) {
                Question question = form.getActiveQuestions().get(i);
                System.out.println("############### QUESTION: " + question.getName());

                boolean unknown = BasicProperties.isUnknownVisible(question);
                QuestionValue value = Unknown.getInstance();

                if (question instanceof QuestionChoice) {
                    value = processChoices(question, unknown);
                } else if (question instanceof QuestionNum) {
                    // TODO: to be implemented
                    System.out.println("numerical value handling not yet implemented..");
                    System.exit(0);
                }

                // answer question
                session.getBlackboard().addValueFact(FactFactory.createUserEnteredFact(question, value));
                System.out.println("--> " + ValueUtils.getVerbalization(question, value, Locale.ROOT));

                if (session.getBlackboard().getSolutions(Rating.State.ESTABLISHED).size() > 0) {
                    Solution sol = session.getBlackboard().getSolutions(Rating.State.ESTABLISHED).get(0);
                    System.out.println("SOLUTION: " + sol.getName());
                    solution = sol.getName();
                    System.out.println("RECOMMENDED ACTION: " + sol.getInfoStore().getValue(MMInfo.DESCRIPTION));
                }
            }
        }
        saveToFile(session, sessionPath);
        return solution;
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
     * @param sessionPath - directory path to save session to
     * @throws IOException
     */
    private static void saveToFile(Session session, String sessionPath) throws IOException {
        System.out.println("saving session to file..");
        OutputStream out = new FileOutputStream(sessionPath);
        SessionPersistenceManager.getInstance().saveSessions(
                out, SessionConversionFactory.copyToSessionRecord(session)
        );
        out.flush();
        out.close();
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
        System.out.println("Running customer XPS..");
        try {
            GatewayServer server = new GatewayServer(new Main());
            server.start();

            Logger rootLogger = LogManager.getLogManager().getLogger("");
            rootLogger.setLevel(Level.SEVERE);
            for (Handler h : rootLogger.getHandlers()) {
                h.setLevel(Level.SEVERE);
            }
            System.out.println("server runs..");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
