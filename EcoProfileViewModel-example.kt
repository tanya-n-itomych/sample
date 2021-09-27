package nda.main.eco_profile.viewmodel

import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import nda.R
import nda.base.BaseViewModel
import nda.base.NetworkError
import nda.base.extensions.mutable
import nda.base.extensions.withProgress
import nda.base.model.SelectableModel
import nda.base.model.TransportType
import nda.base.requireValue
import nda.base.shared_preferences.SharedPreferencesHelper
import nda.base.utils.formatDuration
import nda.base.utils.formatEcology
import nda.main.NdaApplication
import nda.main.eco_profile.model.ChangeTransportMode
import nda.main.eco_profile.model.PerformancePeriod
import nda.main.eco_profile.model.response.EcoProfileData
import nda.main.eco_profile.model.response.UsualJourneyData
import nda.main.eco_profile.model.response.UsualLegData
import nda.main.eco_profile.model.response.UsualRoundTripData
import nda.main.eco_profile.repository.EcoProfileRepository
import nda.main.profile_registration_common.mobility_settings.data.model.MobilitySettings
import nda.main.recommendations.data.model.AddressLocal
import nda.main.recommendations.data.model.Coordinate
import nda.formatter.DateTimeFormatter
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

/**
 * ViewModel class for providing data Eco Profile UI
 *
 */

class EcoProfileViewModel(private val ecoProfileRepository: EcoProfileRepository) :
    BaseViewModel<EcoProfileNavigation>() {

    private val context by lazy { MobaltApplication.staticContext!! }

    val isProgress = mutable(false)

    private var ecoProfile: EcoProfileData? = null
    private val performancePeriod = mutable(PerformancePeriod.TODAY)
    val performancePeriodName = performancePeriod.map { context.getString(it.periodName) }

    val ecologyProgress = mutable(0.0f)
    val savingsProgress = mutable(0.0f)
    val fitnessProgress = mutable(0.0f)
    val ecologyValue = mutable<CharSequence>()
    val savingsValue = mutable<CharSequence>()
    val fitnessValue = mutable<CharSequence>()

    // values for EditTrip
    private var needReload = true
    private var usualRoundTrip = mutable<UsualRoundTripData>()
    val outwardJourney = mutable<List<UsualLegData>>()
    val returnJourney =  mutable<List<UsualLegData>>()
    val isRouteDifferent = mutable<Boolean>()
    val isJourneySame = mutable<Boolean>()

    // values for EditTripLeg
    private var currentEditLegData: UsualLegData? = null
    private var currentEditPosition: Int = 0
    private var currentIsOutwardTrip: Boolean = true
    val fromAddressLocal = mutable<AddressLocal>()
    val toAddressLocal = mutable<AddressLocal>()
    val isEdit = mutable(false)
    var canEditStart = mutable(false)
    var canEditEnd = mutable(false)
    var canDelete = mutable(false)
    val transportTypes = mutable<List<SelectableModel<TransportType>>>()

    var mobilitySettings: MobilitySettings? = null

    fun fetchEcoProfile() {
        val userId = SharedPreferencesHelper.userId
        if (userId != null) {
            viewModelScope.launch(IO) {
                withProgress(isProgress) {
                    handleResult(
                            ecoProfileRepository.fetchEcoProfile(userId),
                            {
                                ecoProfile = it
                                mapEcoProfile()
                            },
                            {
                                navigateToError(it)
                            }
                    )
                }
            }
        }
    }

    private fun mapEcoProfile(coefficient: Int = DAILY_COEFFICIENT) {
        ecologyProgress.postValue(ecoProfile?.co2SavingBenchmark?.percentage)
        savingsProgress.postValue(ecoProfile?.moneySavingBenchmark?.percentage)
        fitnessProgress.postValue(ecoProfile?.fitnessBenchmark?.percentage)

        updateValues(coefficient)
    }

    fun onPerformancePeriodSelected(period: PerformancePeriod) {
        performancePeriod.postValue(period)
        val coefficient =
            when (period) {
                PerformancePeriod.TODAY -> DAILY_COEFFICIENT
                PerformancePeriod.MONTH -> MONTHLY_COEFFICIENT
                PerformancePeriod.YEAR -> YEARLY_COEFFICIENT
            }

        updateValues(coefficient)
    }

    /**
     * Updates all values given the [coefficient]
     */
    private fun updateValues(coefficient: Int) {
        ecologyValue.postValue(buildEcologyValue(coefficient))
        savingsValue.postValue(buildSavingsValue(coefficient))
        fitnessValue.postValue(buildFitnessValue(coefficient))
    }

    /**
     * Builds the ecology value multiplied by the [coefficient]
     */
    private fun buildEcologyValue(coefficient: Int): String? {
        val ecologyValue = ecoProfile?.co2SavingBenchmark?.value
        if (ecologyValue != null) {
            return context.getString(
                    R.string.eco_profile_ecology_value,
                    formatEcology((ecologyValue * coefficient).toInt())
            )
        }

        return null
    }

    /**
     * Builds the savings value multiplied by the [coefficient]
     */
    private fun buildSavingsValue(coefficient: Int): String? {
        val savingsValue = ecoProfile?.moneySavingBenchmark?.value
        if (savingsValue != null) {
            return context.getString(
                    R.string.eco_profile_savings_value,
                    (savingsValue * coefficient).toInt().toString()
            )
        }

        return null
    }

    /**
     * Builds the fitness value multiplied by the [coefficient]
     */
    private fun buildFitnessValue(coefficient: Int): String? {
        val fitnessValue = ecoProfile?.fitnessBenchmark?.value
        if (fitnessValue != null) {
            return context.getString(
                    R.string.eco_profile_fitness_value,
                    formatDuration((fitnessValue * 60 * coefficient).toInt()) //Fitness value is in hours
            )
        }

        return null
    }

    fun setupEditTrip() {
        if(needReload) {
            val userId = SharedPreferencesHelper.userId
            if (userId != null) {
                fetchEditTrip(userId)
            }
        }
    }

    private fun fetchEditTrip(userId: Int) {
        viewModelScope.launch(IO) {
            withProgress(isProgress) {
                handleResult(
                    ecoProfileRepository.fetchUsualRoundTrip(userId),
                    {
                        mapRoundTrip(it)
                        needReload = false
                    },
                    {
                        navigateToError(it)
                    }
                )
            }
        }
    }

    private fun mapRoundTrip(trip:UsualRoundTripData) {
        usualRoundTrip.postValue(trip)
        outwardJourney.postValue(trip.outwardJourney.journeyLegList)
        returnJourney.postValue(trip.returnJourney?.journeyLegList )
        isRouteDifferent.postValue(!(trip.isJourneySame()))
        isJourneySame.postValue(trip.isJourneySame())
    }

    fun clearReturnJourney() {
        usualRoundTrip.value?.returnJourney = null
    }

    fun createReturnJourney() {
        if (usualRoundTrip.value?.returnJourney == null)
            usualRoundTrip.value?.createReturnJourney()
        usualRoundTrip.value?.returnJourney?.let {
            returnJourney.postValue(it.journeyLegList)
        }
    }

    fun navigateToEditTripLegClicked(position: Int, isEdit: Boolean, isOutwardTrip: Boolean, isLastPlus: Boolean) {

        val currentEditJourney = if (isOutwardTrip) outwardJourney.value else returnJourney.value

        currentIsOutwardTrip = isOutwardTrip
        currentEditJourney?.get(position)?.let {
            currentEditLegData = UsualLegData(
                it.startLocation,
                it.endLocation,
                it.startLatitude,
                it.startLongitude,
                it.endLatitude,
                it.endLongitude,
                it.distance,
                it.travelModeCode,
                it.travelModeDescription
            )
        }

        currentEditPosition = position
        canDelete.postValue(currentEditJourney?.size ?: 0 > 1)

        val cantEditEnd = isLastPlus || (isEdit && position == (currentEditJourney?.size ?: 0) - 1)
        canEditEnd.postValue(!cantEditEnd)
        canEditStart.postValue(position != 0)
        this.isEdit.postValue(isEdit)

        val newTransportTypes = ArrayList<SelectableModel<TransportType>>()
        TransportType.getUsualTripTransports().forEach {
            newTransportTypes.add(SelectableModel(it, it.typeCode == currentEditLegData?.travelModeCode))
        }
        transportTypes.postValue(newTransportTypes)

        changeAddresses()
        navigateToEditTripLeg()
    }

    fun transportTypeSelected(type: TransportType) {
        currentEditLegData?.travelModeCode = type.typeCode
    }

    fun  applyFromAddress(addressLocal: AddressLocal) {
        currentEditLegData?.startLocation = addressLocal.fullAddress?:""
        currentEditLegData?.startLatitude = addressLocal.coordinate?.latitude?: 0.0
        currentEditLegData?.startLongitude = addressLocal.coordinate?.longitude?: 0.0
        changeAddresses()
    }

    fun  applyToAddress(addressLocal: AddressLocal) {
        currentEditLegData?.endLocation = addressLocal.fullAddress?:""
        currentEditLegData?.endLatitude = addressLocal.coordinate?.latitude?: 0.0
        currentEditLegData?.endLongitude = addressLocal.coordinate?.longitude?: 0.0
        changeAddresses()
    }

    private fun changeAddresses() {
        fromAddressLocal.postValue( AddressLocal(
                null,
                Coordinate(currentEditLegData?.startLatitude ?: 0.0, currentEditLegData?.startLongitude ?: 0.0),
                null,
                null,
                null,
                currentEditLegData?.startLocation
        ))
        toAddressLocal.postValue(AddressLocal(
                null,
                Coordinate(currentEditLegData?.endLatitude ?: 0.0, currentEditLegData?.endLongitude ?: 0.0),
                null,
                null,
                null,
                currentEditLegData?.endLocation
        ))
    }

    fun saveTripData() {
        usualRoundTrip.value?.outwardJourney?.timetable =
                DateTimeFormatter.getTomorrowDateTimeString(usualRoundTrip.value?.outwardJourney?.timetable, DateTimeFormatter.serverDateTimeFormat)
        usualRoundTrip.value?.returnJourney?.timetable =
                DateTimeFormatter.getTomorrowDateTimeString(usualRoundTrip.value?.returnJourney?.timetable, DateTimeFormatter.serverDateTimeFormat)

        viewModelScope.launch(IO) {
            withProgress(isProgress) {
                handleResult(
                    ecoProfileRepository.updateUsualRoundTrip(usualRoundTrip.value!!),
                    { handleSaveTripResult(it) },
                    { navigateToError(it) }
                )
            }
        }
    }

    /**
     * Handles the [usualRoundTripData] saved on the server
     */
    private fun handleSaveTripResult(usualRoundTripData: UsualRoundTripData) {
        if (usualRoundTripData.usualTransportationType != null)
            navigateToDialog(ChangeTransportMode.PRIMARY_TRANSPORT_CHANGED.messageRes)
        else
            navigateBack()
    }

    fun saveTripLeg() = changeTripLeg(true)

    fun deleteTripLeg() = changeTripLeg(false)

    private fun changeTripLeg(isSave:Boolean) {
        val newRoundTrip = usualRoundTrip.value

        if(!isSave) // delete mode
            newRoundTrip?.deleteLegData(currentIsOutwardTrip, currentEditPosition, currentEditLegData!!)
        else if(isEdit.value == true) // edit mode
            newRoundTrip?.editLegData(currentIsOutwardTrip, currentEditPosition, currentEditLegData!!)
        else if(isEdit.value == false) { // add mode
            if (canEditEnd.value == false)
                currentEditPosition++
            newRoundTrip?.addLegData(currentIsOutwardTrip, currentEditPosition, currentEditLegData!!)
        }

        val journeyForValidate = if(currentIsOutwardTrip) newRoundTrip?.outwardJourney else newRoundTrip?.returnJourney
        journeyForValidate?.timetable = DateTimeFormatter.getTomorrowDateTimeString(journeyForValidate?.timetable, DateTimeFormatter.serverDateTimeFormat)

        if (journeyForValidate != null && newRoundTrip != null)
            validateJourney(journeyForValidate, newRoundTrip)
    }

    private fun validateJourney(journey: UsualJourneyData, newRoundTrip: UsualRoundTripData) {
        viewModelScope.launch(IO) {
            withProgress(isProgress) {
                handleResult(
                    ecoProfileRepository.validateUsualJourney(journey),
                    {
                            if(currentIsOutwardTrip)
                                newRoundTrip.outwardJourney = it
                            else
                                newRoundTrip.returnJourney = it
                            mapRoundTrip(newRoundTrip)
                            navigateBack()
                    },
                    {
                        navigateToError(it)
                    }
                )
            }
        }
    }

    fun clearTripData() {
        needReload = true
    }

    private fun navigateToError(error: NetworkError?) = navigateTo(EcoProfileNavigation.EcoError(error))

    fun navigateToEditTrip() = navigateTo(EcoProfileNavigation.EditTripData)

    private fun navigateToEditTripLeg() {
        navigateTo(EcoProfileNavigation.EditTripLegData)
    }

    fun navigateToChooseFromAddress() =
        navigateTo(EcoProfileNavigation.ChooseAddress(fromAddressLocal.value, true))


    fun navigateToChooseToAddress() =
        navigateTo(EcoProfileNavigation.ChooseAddress(toAddressLocal.value, false))


    fun navigateToSelectPerformancePeriod() =
        navigateTo(EcoProfileNavigation.SelectPerformancePeriod(performancePeriod.requireValue()))

    private fun navigateToDialog(messageRes: Int) =
        navigateTo(EcoProfileNavigation.ChangeTransportModeDialog(messageRes))

    fun navigateBack() = navigateTo(EcoProfileNavigation.Back)

    companion object {
        private const val DAILY_COEFFICIENT = 1
        private const val MONTHLY_COEFFICIENT = 20
        private const val YEARLY_COEFFICIENT = 240
    }

}