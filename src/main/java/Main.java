import java.util.Scanner;

/** Single application entry point for the integrated SmartBank project. */
public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        UserAuthentication auth = new UserAuthentication();
        Registration registration = new Registration(auth, scanner);
        Login login = new Login(auth, scanner);
        UserDashboard dashboard = new UserDashboard(auth, scanner);

        System.out.println("====================================================");
        System.out.println("       SMARTBANK - SMART BANKING SYSTEM              ");
        System.out.println("       Banking Operations & Fraud Detection          ");
        System.out.println("====================================================");
        while (true) {
            System.out.println("\n1. Register new customer\n2. Login\n3. Test MySQL connection\n4. Exit");
            System.out.print("Choose an option: ");
            String choice;
            try { choice = scanner.nextLine().trim(); }
            catch (java.util.NoSuchElementException e) { break; }
            switch (choice) {
                case "1": registration.start(); break;
                case "2": if (login.start()) dashboard.show(); break;
                case "3": DatabaseConnection.main(new String[0]); break;
                case "4": System.out.println("Thank you for using SmartBank."); scanner.close(); return;
                default: System.out.println("Invalid option. Please choose 1-4.");
            }
        }
        scanner.close();
    }
}
