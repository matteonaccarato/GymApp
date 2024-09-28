package com.example.gymapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import com.example.gymapp.databinding.FragmentHomeBinding
import com.example.gymapp.login.WelcomeActivity
import com.firebase.ui.auth.AuthUI
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.CropSquareTransformation
import java.util.Calendar
import java.util.Locale
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.sqrt

class HomeFragment : Fragment(), SensorEventListener {

    companion object {
        fun newInstance() = HomeFragment()
        const val ID = "Home"
    }

    /** DB */
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private lateinit var binding : FragmentHomeBinding
    private val viewModel: GymViewModel by activityViewModels()
    private lateinit var picker : MaterialTimePicker
    private lateinit var calendar : Calendar
    private var alarmManager : AlarmManager? = null
    private var sensorManager: SensorManager? = null
    private val accelerometerData = mutableListOf<FloatArray>()

    private var running = false
    private var previousTotalSteps = 0f
    private var totalSteps = 0f
    private val SAMPLE_RATE = 50 // 50Hz
    private val FFT_WINDOW = 256
    private val MIN_STEPS_FREQ = 1.0
    private val MAX_STEPS_FREQ = 3.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = requireContext().getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        loadData()
        resetSteps()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = Navigation.findNavController(view)

        if (firebaseAuth.currentUser == null) {
            val intentWelcomeActivity = Intent(requireContext(), WelcomeActivity::class.java)
            startActivity(intentWelcomeActivity)
        }

        /* SHOW USER INFO */
        showUserData()

        /* SHOW USER's TRAINING PLANS */
        showTrainingPlans(view)

        /* BUTTONS */
        binding.homeBtnSignOut.setOnClickListener {
            AuthUI.getInstance()
                .signOut(requireContext())
                .addOnCompleteListener {
                    val intent = Intent(requireContext(), WelcomeActivity::class.java)
                    startActivity(intent)
                }
        }

        /* TRAINING ALARM */
        binding.homeBtnTrainingReminder.setOnClickListener {
            when (viewModel.alarmInfo.status) {
                AlarmStatus.NOT_SET -> showTimePicker()
                AlarmStatus.SET -> {
                    if (viewModel.alarmInfo.timeInMillis > Calendar.getInstance().timeInMillis)
                        cancelAlarm()
                    else showTimePicker()
                }
            }
        }

        /* NAVIGATION */
        binding.homeBtnEdit.setOnClickListener {
            navController.navigate(R.id.action_homeFragment_to_accountFragment)
        }

        binding.btnAdd.setOnClickListener {
            viewModel.viewTraining = false
            navController.navigate(R.id.action_homeFragment_to_trainingFragment)
        }
    }

    /*
     * input: view
     * output: none
     * note: extracts the data from the database and displays the training plans in the appropriate section
     */
    private fun showTrainingPlans(view: View){
        val navController = Navigation.findNavController(view)

        viewModel.extractDocument{
            // Handle the situation when there are training plans
            if (viewModel.training_Data_Document.value?.isNotEmpty() ?: false) {
                binding.homeFragment.removeView(binding.infoTrainingEmpty)

                val layout: TableLayout = binding.tableLayout
                layout.removeAllViews()
                var row = TableRow(requireContext())

                row.gravity = Gravity.CENTER
                viewModel.trainingPlanContainer.clear()
                var i = 0
                // Iterate on all training plans
                for (element in viewModel.training_Data_Document.value!!) {
                    val textView = TextView(requireContext())
                    textView.text = element
                    textView.setPadding(0,0,0,50)

                    row.addView(textView)
                    // Add the training plan to training Plan Container
                    viewModel.trainingPlanContainer.add(textView)
                    i++
                    // Display no more than 3 training plans on the same row
                    if (i == 3){
                        layout.addView(row)
                        row = TableRow(requireContext())
                        i=0
                    }
                }
                layout.addView(row)
            }
            else{
                binding.infoTrainingEmpty.text = resources.getString(R.string.no_training_plans)
            }

            // Iterate on all training plans
            for (element in viewModel.trainingPlanContainer){
                // Manage clicks on training plan
                element.setOnClickListener {
                    // Extract the position of the training Plan from the training Plan Container to find the information in the DB if necessary
                    viewModel.trainingPlanId = viewModel.trainingPlanContainer.indexOf(it)

                    // View the training plan
                    if (viewModel.trainingPlanId != -1){
                        viewModel.viewTraining = true
                        navController.navigate(R.id.action_homeFragment_to_trainingFragment)
                    }
                }
            }
        }
    }

    /**
     * Load user's personal data from viewmodel
     */
    private fun showUserData() {
        viewModel.userPersonalData.observe(viewLifecycleOwner, Observer {
            val customMsg =
                if ((viewModel.userPersonalData.value?.username) != "")
                    viewModel.userPersonalData.value?.username
                else if (viewModel.userPersonalData.value?.name != "")
                    viewModel.userPersonalData.value?.name
                else ""
            val txtWelcome = when (customMsg) {
                "" -> resources.getString(R.string.welcome_message)
                else -> resources.getString(R.string.welcome_message_custom, customMsg)
            }
            for (pair in arrayOf(
                binding.homeTxtWelcomeBack to txtWelcome,
                binding.homeTxtName to viewModel.userPersonalData.value?.name,
                binding.homeTxtUsername to viewModel.userPersonalData.value?.username,
                binding.homeTxtBirthDate to viewModel.userPersonalData.value?.dateBirth,
                binding.homeTxtSex to viewModel.userPersonalData.value?.sex?.displayName
            )) {
                pair.first.text = pair.second
            }
        })

        viewModel.userPhotoUrl.observe(viewLifecycleOwner) { uri ->
            val rotationDegrees = if (viewModel.isImageRotated) 0F else 90F
            Picasso.get().load(uri)
                .transform(CropSquareTransformation())
                .fit().centerInside()
                .rotate(rotationDegrees)
                .error(R.drawable.charles_leclerc)
                .placeholder(R.drawable.avatar_default)
                .into(binding.imgProfile)
        }

        // ALARM | read file to load current alarm info previously stored in the internal storage
        val file = requireContext().getFileStreamPath(DBManager.INTERNAL_FILENAME)
        if (file.exists()) {
            val contents = file.readText().split(AlarmInfo.CONTENT_SEPARATOR)
            Log.d(MainActivity.TAG, "FILE => $contents")
            if (contents.size == AlarmInfo.INFO_TO_STORE_IN_FILE) {
                viewModel.alarmInfo = AlarmInfo(AlarmStatus.fromInt(contents[0].toIntOrNull()), contents[1].toLongOrNull() ?: AlarmInfo.DEFAULT_TIME_IN_MILLIS)
                binding.homeBtnTrainingReminder.text = if (viewModel.alarmInfo.status == AlarmStatus.SET && viewModel.alarmInfo.timeInMillis > Calendar.getInstance().timeInMillis)
                    resources.getString(
                        R.string.training_reminder_set,
                        String.format(
                            Locale.getDefault(),
                            resources.getString(R.string.alarm_format),
                            Calendar.getInstance().apply { timeInMillis = viewModel.alarmInfo.timeInMillis }.get(Calendar.HOUR_OF_DAY),
                            Calendar.getInstance().apply { timeInMillis = viewModel.alarmInfo.timeInMillis }.get(Calendar.MINUTE)))
                else resources.getString(R.string.training_reminder_no_set)
            } else Log.e(MainActivity.TAG, "FILE | wrong number of parameters to interpret content")
        } else Log.e(MainActivity.TAG, "NO FILE")
    }

    /**
     * Show time picker to select the time to receive the alarm at
     */
    private fun showTimePicker() {
        calendar = Calendar.getInstance()
        picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .setTitleText(AlarmReceiver.TIME_PICKER_TEXT)
            .build()
        picker.show(parentFragmentManager, AlarmReceiver.CHANNEL_ID)
        picker.addOnPositiveButtonClickListener {
            setAlarm(picker.hour, picker.minute)
        }
    }

    /**
     * Set alarm through AlarmManager and store its information in the internal storage
     */
    private fun setAlarm(hour: Int, minute: Int) {
        val intent = Intent(requireContext(), AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val alarmCalendar = Calendar.getInstance()
        alarmCalendar.set(Calendar.HOUR_OF_DAY, hour)
        alarmCalendar.set(Calendar.MINUTE, minute)
        alarmCalendar.set(Calendar.SECOND, 0)
        alarmCalendar.set(Calendar.MILLISECOND, 0)

        if (alarmCalendar.timeInMillis < Calendar.getInstance().timeInMillis) {
            // alarm set in the past => alarm will be set for the following day at the selected time
            alarmCalendar.timeInMillis += AlarmReceiver.ONE_DAY_IN_MILLIS
        }

        alarmManager = requireContext().getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager!!.set(
            AlarmManager.RTC_WAKEUP, alarmCalendar.timeInMillis,
            pendingIntent
        )

        // Store alarm status
        storeAlarmInfo(AlarmInfo(AlarmStatus.SET, alarmCalendar.timeInMillis))

        binding.homeBtnTrainingReminder.text = resources.getString(
            R.string.training_reminder_set,
            String.format(Locale.getDefault(), resources.getString(R.string.alarm_format), alarmCalendar.get(Calendar.HOUR_OF_DAY), alarmCalendar.get(Calendar.MINUTE))
        )
        Toast.makeText(requireContext(), resources.getString(R.string.home_fragment_alarm_set), Toast.LENGTH_SHORT).show()
    }

    /**
     * Cancel alarm previously set (through AlarmManager)
     */
    private fun cancelAlarm() {
        alarmManager = requireContext().getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), AlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE)
        alarmManager!!.cancel(pendingIntent)

        storeAlarmInfo(AlarmInfo(AlarmStatus.NOT_SET))
        binding.homeBtnTrainingReminder.text = resources.getString(R.string.training_reminder_no_set)
        Toast.makeText(requireContext(), resources.getString(R.string.home_fragment_alarm_cancelled), Toast.LENGTH_SHORT).show()
    }

    /**
     * Store alarm info in the internal store [status;timeInMillis]
     * e.g. [SET;19900302]
     */
    private fun storeAlarmInfo(alarmInfo: AlarmInfo) {
        viewModel.alarmInfo = alarmInfo
        requireContext().openFileOutput(DBManager.INTERNAL_FILENAME, Context.MODE_PRIVATE).use {
            Log.d(MainActivity.TAG, "FILE | ${alarmInfo.toFile()} written in ${DBManager.INTERNAL_FILENAME}")
            it.write(alarmInfo.toFile().toByteArray())
        }
    }

    /**
     * Called when the fragment is visible to the user and actively running
     * Get accelerometer and register Listener
     */
    override fun onResume() {
        super.onResume()
        running = true
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Toast.makeText(requireContext(), "No sensor detected on this device", Toast.LENGTH_SHORT).show()
        } else {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

    }

    /**
     * Compute potential new steps through FFT
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (running && event !== null) {
            // Add new values to buffer
            accelerometerData.add(event.values.clone())
            if (accelerometerData.size >= FFT_WINDOW) {
                val steps = calculateStepsUsingFFT(accelerometerData)
                Log.d("HomeFragment", "Steps detected (FFT): $steps")
                accelerometerData.clear() // clear buffer
                totalSteps += steps       // increment total steps counter
            }

            // Update UI
            binding.tvStepsTaken.text = ("${totalSteps.toInt()}")
            binding.progressCircular.apply {
                setProgressWithAnimation(totalSteps)
            }
        }
    }


    /**
     * Compute steps through FFT (Fast Fourier Transformation)
     */
    private fun calculateStepsUsingFFT(data: List<FloatArray>): Int {
        // Extract the magnitude of the accelerometer vector ( sqrt(X^2 + Y^2 + Z^2) )
        val magnitudes = data.map { it ->
            sqrt((it[0] * it[0] + it[1] * it[1] + it[2] * it[2]).toDouble()).toFloat()
        }.toFloatArray()

        // Apply FFT
        val fft = FloatFFT_1D(magnitudes.size.toLong())
        fft.realForward(magnitudes)

        // Calculate power spectrum
        val powerSpectrum = magnitudes.map { it * it }
        // Find dominant frequency (related to steps)
        val dominantFrequencyIndex = powerSpectrum.indexOf(powerSpectrum.drop(1).maxOrNull() ?: 0f) + 1

        // Calculate frequency
        val stepFrequency = dominantFrequencyIndex * (SAMPLE_RATE.toDouble() / magnitudes.size)
        Log.d("HomeFragment", "StepFreq $stepFrequency")

        // Filter frequency (standard walking frequency is in [1-3] Hz)
        if (stepFrequency in MIN_STEPS_FREQ..MAX_STEPS_FREQ) {
            // Return estimated steps
            return (stepFrequency * (data.size / SAMPLE_RATE)).toInt()
        }

        // No steps detected
        return 0
    }

    private fun resetSteps() {
        binding.tvStepsTaken.setOnClickListener {
            Toast.makeText(requireContext(), "Long tap to reset steps", Toast.LENGTH_SHORT).show()
        }

        binding.tvStepsTaken.setOnLongClickListener() {
            previousTotalSteps = totalSteps
            binding.tvStepsTaken.text = 0.toString()
            saveData()
            true
        }
    }

    private fun saveData() {
        val sharedPreferences = requireContext().getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("key_previous_steps", previousTotalSteps)
        editor.apply()
    }

    private fun loadData() {
        val sharedPreferences = requireContext().getSharedPreferences("myPrefs", Context.MODE_PRIVATE)
        val savedNumber = sharedPreferences.getFloat("key_previous_steps", 0f)
        Log.d("HomeFragment", "$savedNumber")
        previousTotalSteps = savedNumber
          totalSteps = previousTotalSteps
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}