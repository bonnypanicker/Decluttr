import android.net.Uri
val sharedUrl = "Check out this app: https://play.google.com/store/apps/details?id=com.example.app"
println(Uri.parse(sharedUrl).getQueryParameter("id"))
