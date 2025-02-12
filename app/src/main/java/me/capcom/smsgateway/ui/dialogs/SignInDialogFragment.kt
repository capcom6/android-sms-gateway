package me.capcom.smsgateway.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import me.capcom.smsgateway.R

class SignInDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the Builder class for convenient dialog construction.
        return AlertDialog.Builder(requireActivity())
            .apply {
                setView(R.layout.fragment_sign_in)
                setTitle("First start")
                setMessage("To add the device to existing account please fill in the form and click Sign In. Or skip the form and click Register to create a new account.")
                setPositiveButton("Register") { dialog, id ->
                    // START THE GAME!
                }
                setNeutralButton("Sign In") { dialog, id ->

                }
                setNegativeButton("Cancel") { dialog, id ->
                    // User cancelled the dialog.
                }
            }
            .create()
    }

    companion object {
        fun newInstance(): SignInDialogFragment = SignInDialogFragment()
    }
}