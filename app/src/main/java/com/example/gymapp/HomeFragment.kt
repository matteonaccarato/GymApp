package com.example.gymapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.view.contains
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import com.example.gymapp.databinding.FragmentHomeBinding
import com.example.gymapp.login.WelcomeActivity
import com.firebase.ui.auth.AuthUI
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.CropSquareTransformation

class HomeFragment : Fragment() {

    companion object {
        fun newInstance() = HomeFragment()
    }

    private lateinit var binding : FragmentHomeBinding
    private val viewModel: GymViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.extractDocument()
        if (DBManager.training_Data_Document.size > 0) {
            val layout: TableLayout = binding.tableLayout
            val row = TableRow(requireContext())
            viewModel.trainingPlanContainer.clear()
            for (element in DBManager.training_Data_Document) {
                val textView = TextView(requireContext())
                textView.text = element
                row.addView(textView)
                viewModel.trainingPlanContainer.add(textView)
            }
            layout.addView(row)
        }



        /* SHOW USER INFO */
        showUserData()

        /* SHOW USER's TRAINING PLANS */
        // TODO => similar to previous observer


        /* BUTTONS */
        binding.homeBtnSignOut.setOnClickListener {
            AuthUI.getInstance()
                .signOut(requireContext())
                .addOnCompleteListener {
                    val intent = Intent(requireContext(), WelcomeActivity::class.java)
                    startActivity(intent)
                }
        }

        /* NAVIGATION */
        val navController = Navigation.findNavController(view)
        binding.homeBtnEdit.setOnClickListener {
            navController.navigate(R.id.action_homeFragment_to_accountFragment)
        }

        binding.btnAdd.setOnClickListener {
            viewModel.viewTraining = false
            navController.navigate(R.id.action_homeFragment_to_trainingFragment)
        }

        for (element in viewModel.trainingPlanContainer){
            element.setOnClickListener {
                for (el in viewModel.trainingPlanContainer){
                    Log.d("Element: ", "${view.id} == ${el.id}")

                    if (el == element){
                        viewModel.trainingPlanId = viewModel.trainingPlanContainer.indexOf(el)
                        Log.d("Tag", "${viewModel.trainingPlanContainer}")
                        if (viewModel.trainingPlanId != -1){
                            viewModel.viewTraining = true
                            navController.navigate(R.id.action_homeFragment_to_trainingFragment)
                        }
                        break
                    }
                }
            }
        }
    }

    /**
     *
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
    }
}