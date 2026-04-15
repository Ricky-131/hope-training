import java.util.Scanner;
import java.util.HashMap;

public class AuthCLI {
    private static HashMap<String, String> users = new HashMap<>();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("1. Reg 2. Login 3. Exit: ");
            int opt = sc.nextInt();
            sc.nextLine();
            if (opt == 3) break;
            
            System.out.print("User: ");
            String u = sc.nextLine();
            System.out.print("Pass: ");
            String p = sc.nextLine();

            if (opt == 1) {
                users.put(u, p);
                System.out.println("Saved.");
            } else if (opt == 2) {
                if (p.equals(users.get(u))) System.out.println("Success.");
                else System.out.println("Failed.");
            }
        }
        sc.close();
    }
}