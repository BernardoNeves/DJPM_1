package ipca.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.webkit.PermissionRequest
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.createBitmap
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.clear
import coil.load
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import ipca.weatherapp.databinding.ActivityMainBinding
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    val api: String = "1ff53cca53976baf82b90a7ee8806eb9"
    lateinit var city: String
    lateinit var lat: String
    lateinit var lon: String
    lateinit var mBitmap: Bitmap
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        binding.camera.setOnClickListener{
            cameraCheckPermission()
        }
        binding.cancel.setOnClickListener{
            binding.image.clear()
            findViewById<ImageButton>(R.id.camera).visibility = View.VISIBLE
            findViewById<ImageButton>(R.id.cancel).visibility = View.GONE
            findViewById<ImageButton>(R.id.settings).visibility = View.VISIBLE
            findViewById<ImageButton>(R.id.share).visibility = View.GONE
        }
        binding.settings.setOnClickListener{
            try{
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch(e: ActivityNotFoundException){
                e.printStackTrace()
            }
        }

        val image = binding.image.drawable
        binding.share.setOnClickListener{
            //val mBitmap = (image as BitmapDrawable).bitmap
            val path = MediaStore.Images.Media.insertImage(contentResolver,mBitmap,"Image Description", null)
            val uri = Uri.parse(path)

            val city = findViewById<TextView>(R.id.address).text
            val weather = findViewById<TextView>(R.id.status).text
            val temp = findViewById<TextView>(R.id.temp).text

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "image/*"
            shareIntent.putExtra(Intent.EXTRA_TEXT, "It's $temp in $city, the weather is $weather")
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            startActivity(Intent.createChooser(shareIntent, "Share Image"))
        }

        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener{
            weatherTask().execute()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun cameraCheckPermission() {
        Dexter.withContext(this)
            .withPermissions(android.Manifest.permission.CAMERA, android.Manifest.permission.READ_EXTERNAL_STORAGE).withListener(
                object : MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        report?.let{
                            if (report.areAllPermissionsGranted()){
                                camera()
                            }
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<com.karumi.dexter.listener.PermissionRequest>?,
                        p1: PermissionToken?
                    ) {
                        showRorationalDialogForPermission()
                    }
                }
            ).onSameThread().check()
    }

    private fun camera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(resultCode == Activity.RESULT_OK){
            when(requestCode){
                1->{
                    mBitmap = data?.extras?.get("data") as Bitmap
                    binding.image.load(mBitmap){
                        crossfade(true)
                        crossfade(1000)
                    }
                    findViewById<ImageButton>(R.id.camera).visibility = View.GONE
                    findViewById<ImageButton>(R.id.cancel).visibility = View.VISIBLE
                    findViewById<ImageButton>(R.id.settings).visibility = View.GONE
                    findViewById<ImageButton>(R.id.share).visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showRorationalDialogForPermission() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature.")
            .setPositiveButton("Go to settings"){_,_->

                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch(e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){dialog, _->
                dialog.dismiss()
            }.show()
    }

    inner class weatherTask() : AsyncTask<String, Void, String>() {
        override fun onPreExecute() {
            super.onPreExecute()
            fetchLocation()
        }

        override fun doInBackground(vararg p0: String?): String? {
            var response: String?
            response = try {
                URL("https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$api")
                    .readText(Charsets.UTF_8)
                //URL("https://api.openweathermap.org/data/2.5/weather?q=$city&units=metric&appid=$api")
                   // .//readText(Charsets.UTF_8)
            } catch(e: Exception) {
                null
            }
            return response
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            try {
                val jsonObj = JSONObject(result)
                val main = jsonObj.getJSONObject("main")
                val sys = jsonObj.getJSONObject("sys")
                val wind = jsonObj.getJSONObject("wind")
                val clouds = jsonObj.getJSONObject("clouds")
                val weather = jsonObj.getJSONArray("weather").getJSONObject(0)
                val updatedAt:Long = jsonObj.getLong("dt")
                val updatedAtText = "Updated at: "+SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH).format(Date(updatedAt*1000))
                val temp = main.getString("temp")+"ºC"
                val tempMin = "Min temp: "+main.getString("temp_min")+"ºC"
                val tempMax = "Max temp: "+main.getString("temp_max")+"ºC"
                val pressure = main.getString("pressure")+" mb"
                val humidity = main.getString("humidity")+"%"
                val sunrise:Long = sys.getLong("sunrise")
                val sunset:Long = sys.getLong("sunset")
                val windSpeed = wind.getString("speed")+" km/h"
                val cloudiness = clouds.getString("all")+"%"
                val weatherDescription = weather.getString("description")
                val address = jsonObj.getString("name")

                findViewById<TextView>(R.id.address).text = address
                findViewById<TextView>(R.id.updated_at).text = updatedAtText
                findViewById<TextView>(R.id.status).text = weatherDescription.capitalize()
                findViewById<TextView>(R.id.temp).text = temp
                findViewById<TextView>(R.id.temp_min).text = tempMin
                findViewById<TextView>(R.id.temp_max).text = tempMax
                findViewById<TextView>(R.id.sunrise).text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(sunrise*1000))
                findViewById<TextView>(R.id.sunset).text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(sunset*1000))
                findViewById<TextView>(R.id.wind).text = windSpeed
                findViewById<TextView>(R.id.cloudiness).text = cloudiness
                findViewById<TextView>(R.id.pressure).text = pressure
                findViewById<TextView>(R.id.humidity).text = humidity

                Toast.makeText(applicationContext, "Refreshed", Toast.LENGTH_SHORT).show()
            }
            catch (e: Exception)
            {
                Toast.makeText(applicationContext, "Couldn't Refresh", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchLocation() {

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),100)
            return
        }

        val location = fusedLocationProviderClient.lastLocation

        location.addOnSuccessListener {
            if (it != null){
                lat = it.latitude.toString()
                lon = it.longitude.toString()
            }
        }
    }
}
/*
@Entity(tableName = "weather_table")
data class currentWeather(
    @PrimaryKey(autoGenerate = true) val id: Int?,
    @ColumnInfo(name = "address") val address: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "status") val status: String?,
    @ColumnInfo(name = "temp") val temp: String?,
    @ColumnInfo(name = "temp_min") val tempMin: String?,
    @ColumnInfo(name = "temp_max") val tempMax: String?,
    @ColumnInfo(name = "sunrise") val sunrise: String?,
    @ColumnInfo(name = "sunset") val sunset: String?,
    @ColumnInfo(name = "wind") val wind: String?,
    @ColumnInfo(name = "cloudiness") val cloudiness: String?,
    @ColumnInfo(name = "pressure") val pressure: String?,
    @ColumnInfo(name = "humidity") val humidity: String?,
)

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_table")
    fun getALL(): List<Weather>

    @Query("SELECT 1 FROM weather_table WHERE id = max(id)")
    fun getLast(): Weather

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(weather: Weather)

    @Delete
    suspend fun delete(weather: Weather)

    @Query("DELETE FROM weather_table")
    suspend fun deleteALL()
}

@Database(entities = [Weather :: class], version = 1)
abstract class AppDatabase : RoomDatabase(){

    abstract fun studentDao() : WeatherDao

    companion object{
        @Volatile
        private var INSTANCE : AppDatabase? = null

        fun getDatabase(context : Context): AppDatabase{
            val tempInstance = INSTANCE
            if (tempInstance != null){
                return tempInstance
            }
            synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
    */