import dao.ActivityLogDao;
import dao.DatabaseInitializer;
import dao.RefillOrderDao;
import dao.UserDao;
import ui.Authentication;

import java.util.Scanner;

/**
 * Single startup entry point: initialize the database, create shared
 * application resources, and delegate to Authentication.
 */
public class Main {
    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialize();

            Scanner scanner = new Scanner(System.in);
            ActivityLogDao activityLogDao = new ActivityLogDao();
            UserDao userDao = new UserDao(activityLogDao);
            RefillOrderDao refillOrderDao = new RefillOrderDao(activityLogDao);

            Authentication authentication =
                    new Authentication(scanner, userDao, activityLogDao, refillOrderDao);
            authentication.start();
        } catch (Exception e) {
            System.err.println("Application error: " + e.getMessage());
        }
    }
}
