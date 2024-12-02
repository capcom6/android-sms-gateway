package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat

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
}