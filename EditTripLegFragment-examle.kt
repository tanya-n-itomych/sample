package nda.main.eco_profile.ui.trip_data_leg

import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import nda.R
import nda.base.BaseFragment
import nda.base.itemdecoration.SpacingDecoration
import nda.databinding.FragmentEditTripLegBinding
import nda.main.eco_profile.viewmodel.EcoProfileViewModel
import nda.main.profile.mobility_settings.ui.ExtraLoadSpaceLayoutManager
import nda.main.profile_registration_common.mobility_settings.quiz_answer_selection.ui.MainTransportTypesAdapter

class EditTripLegFragment : BaseFragment<FragmentEditTripLegBinding>() {

    override val layoutId = R.layout.fragment_edit_trip_leg

    private val viewModel by lazy {
        ViewModelProvider(requireActivity()).get(EcoProfileViewModel::class.java)
    }

    override fun setupBinding(binding: FragmentEditTripLegBinding) {
        binding.vm = viewModel
    }

    override fun setupViews() {
        binding.apply {

            transportFilterRv.apply {
                layoutManager = ExtraLoadSpaceLayoutManager(context).apply { orientation = RecyclerView.HORIZONTAL }
                adapter = MainTransportTypesAdapter(
                        {
                            viewModel.transportTypeSelected(it)
                        },
                        { smoothScrollToPosition(it) }
                )
                addItemDecoration(
                        SpacingDecoration(context, 8f, skipStartOffset = true, skipEndOffset = true)
                )
            }

            viewModel.isEdit.observe(viewLifecycleOwner) { btnDelete.isVisible = it }
            viewModel.canEditStart.observe(viewLifecycleOwner) {
                fromLayout.isIconVisible = it
                if (it) fromLayout.setOnItemClick { viewModel.navigateToChooseFromAddress() }
            }
            viewModel.canEditEnd.observe(viewLifecycleOwner) {
                toLayout.isIconVisible = it
                if (it) toLayout.setOnItemClick { viewModel.navigateToChooseToAddress() }
            }
            viewModel.fromAddressLocal.observe(viewLifecycleOwner) {
                fromLayout.subtitle = it.fullAddress
            }
            viewModel.toAddressLocal.observe(viewLifecycleOwner) {
                toLayout.subtitle = it.fullAddress
            }
            viewModel.canDelete.observe(viewLifecycleOwner) {
                btnDelete.isVisible = it
            }
        }
    }

    companion object {

        @JvmStatic
        fun newInstance() = EditTripLegFragment()

        val TAG = EditTripLegFragment::class.java.name
    }

}