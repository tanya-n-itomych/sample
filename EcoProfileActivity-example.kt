package nda.main.eco_profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import nda.R
import nda.base.ActionDialogFragment
import nda.base.ActivityActionDialogFragment
import nda.base.BaseActivity
import nda.main.address_search.ui.AddressSearchActivity
import nda.main.eco_profile.ui.eco_profile.EcoProfileFragment
import nda.main.eco_profile.ui.eco_profile.PerformancePeriodDialogFragment
import nda.main.eco_profile.ui.trip_data.EditTripFragment
import nda.main.eco_profile.ui.trip_data_leg.EditTripLegFragment
import nda.main.eco_profile.viewmodel.EcoProfileNavigation
import nda.main.eco_profile.viewmodel.EcoProfileViewModel
import nda.main.eco_profile.viewmodel.EcoProfileViewModelFactory
import nda.main.profile.mobility_settings.ui.MobilitySettingsActivity
import nda.main.profile_registration_common.mobility_settings.data.model.MobilitySettings
import nda.main.recommendations.data.model.AddressLocal

/**
 * Activity class for representing Eco Profile UI
 *
 */
class EcoProfileActivity : BaseActivity(),
        ActionDialogFragment.ActionDialogFragmentListener {

    override val layoutId = R.layout.activity_eco_profile

    private val viewModelFactory by lazy { EcoProfileViewModelFactory() }

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(EcoProfileViewModel::class.java)
    }

    private var addressSearchFromResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getParcelableExtra<AddressLocal>(AddressSearchActivity.ARG_ADDRESS)?.let {
                viewModel.applyFromAddress(it)
            }
        }
    }

    private var addressSearchToResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getParcelableExtra<AddressLocal>(AddressSearchActivity.ARG_ADDRESS)?.let {
                viewModel.applyToAddress(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupViewModel()
        addFragment(
            EcoProfileFragment.newInstance(),
            null,
            false,
            R.id.activityContainer
        )
    }

    private fun setupViewModel() {
        intent.getParcelableExtra<MobilitySettings>(ARG_MOBILITY_SETTINGS)?.let {
            viewModel.mobilitySettings = it
        }
        viewModel.observeNavigation(this) { navigateTo(it) }
    }

    override fun onPositiveButtonClick(code: Int) {
        viewModel.navigateBack()
    }
    override fun onNegativeButtonClick() {
        startActivity(MobilitySettingsActivity.getLaunchIntent(this))
        finish()
    }

    private fun navigateTo(navigation: EcoProfileNavigation) {
        when (navigation) {
            EcoProfileNavigation.Back -> onBackPressed()
            is EcoProfileNavigation.SelectPerformancePeriod -> {
                PerformancePeriodDialogFragment
                    .newInstance(navigation.period)
                    .show(supportFragmentManager, PerformancePeriodDialogFragment.TAG)
            }
            EcoProfileNavigation.EditTripData -> navigateToEditTripData()
            is EcoProfileNavigation.ChooseAddress -> navigateToChooseAddress(navigation.isOutward)
            is EcoProfileNavigation.EcoError -> {
                Toast.makeText(this, getString(R.string.server_error_message), Toast.LENGTH_LONG).show()
            }
            is EcoProfileNavigation.EditTripLegData -> navigateToEditTripLegData()
            is EcoProfileNavigation.ChangeTransportModeDialog -> navigateToDialog(navigation.messageRes)
        }
    }

    private fun navigateToEditTripData() {
        replaceFragment(
            EditTripFragment.newInstance(),
            EditTripFragment.TAG,
            true,
            R.id.activityContainer
        )
    }

    private fun navigateToEditTripLegData() {
        replaceFragment(
            EditTripLegFragment.newInstance(),
            EditTripLegFragment.TAG,
            true,
            R.id.activityContainer
        )
    }

    private fun navigateToChooseAddress(isOutward: Boolean) {
        if(isOutward)
            addressSearchFromResultLauncher.launch(AddressSearchActivity.getLaunchIntent(this, null, null))
        else
            addressSearchToResultLauncher.launch(AddressSearchActivity.getLaunchIntent(this, null, null))
    }

    private fun navigateToDialog(messageRes: Int) {
        ActivityActionDialogFragment
                .newInstance(
                        messageRes = messageRes,
                        positiveButtonText = R.string.continue_button,
                        negativeButtonText = R.string.open_mobility_settings
                ).apply { setListener(this@EcoProfileActivity) }
                .show(supportFragmentManager, ActionDialogFragment.TAG)
    }

    companion object {
        private const val ARG_MOBILITY_SETTINGS = "ARG_MOBILITY_SETTINGS"

        fun getLaunchIntent(context: Context, mobilitySettings: MobilitySettings) =
            Intent(context, EcoProfileActivity::class.java).apply {
                putExtra(ARG_MOBILITY_SETTINGS, mobilitySettings)
            }
    }
}