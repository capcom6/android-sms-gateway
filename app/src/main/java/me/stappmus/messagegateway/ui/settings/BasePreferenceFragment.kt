package me.stappmus.messagegateway.ui.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import me.stappmus.messagegateway.R

abstract class BasePreferenceFragment : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backgroundValue = TypedValue()
        requireContext().theme.resolveAttribute(
            android.R.attr.colorBackground,
            backgroundValue,
            true
        )

        view.setBackgroundColor(backgroundValue.data)
    }

    protected fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        configureBackButton(true)
    }

    override fun onPause() {
        configureBackButton(false)
        super.onPause()
    }

    private fun configureBackButton(enabled: Boolean) {
        val toolbar = activity?.findViewById<MaterialToolbar>(R.id.topBar) ?: return
        if (!enabled) {
            toolbar.navigationIcon = null
            toolbar.setNavigationOnClickListener(null)
            return
        }

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.navigationContentDescription = "Back"
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
}
