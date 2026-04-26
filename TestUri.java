import java.net.URI;
public class TestUri {
    public static void main(String[] args) throws Exception {
        String text = "Check out App: https://play.google.com/store/apps/details?id=com.example.app&hl=en";
        try {
            System.out.println("URI: " + new URI(text).getQuery());
        } catch(Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
