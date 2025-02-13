package me.capcom.smsgateway.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import me.capcom.smsgateway.R

class SignInDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.fragment_sign_in, null)
        return AlertDialog.Builder(requireActivity())
            .apply {
                setView(view)
                setTitle("First start")
                setMessage("To add the device to the existing account please fill in the form and click Sign In. Or skip the form and click Register to create a new account.")
                setPositiveButton("Register") { dialog, id ->
                    setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply {
                            putString(KEY_RESULT_CODE, Result.Register.name)
                        }
                    )
                }
                setNeutralButton("Sign In") { dialog, id ->
                    setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply {
                            putString(KEY_RESULT_CODE, Result.SignIn.name)
                            putString(
                                KEY_USERNAME,
                                view.findViewById<EditText>(R.id.editUsername).text.toString()
                            )
                            putString(
                                KEY_PASSWORD,
                                view.findViewById<EditText>(R.id.editPassword).text.toString()
                            )
                        }
                    )
                }
                setNegativeButton("Cancel") { dialog, id ->
                    setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply {
                            putString(KEY_RESULT_CODE, Result.Canceled.name)
                        }
                    )
                }
            }
            .create()
    }

    enum class Result {
        Canceled,
        SignIn,
        Register,
    }

    companion object {
        const val REQUEST_KEY = "SignInDialogFragment"

        private const val KEY_RESULT_CODE = "result_code"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        fun newInstance(): SignInDialogFragment = SignInDialogFragment()

        fun getResult(data: Bundle): Result =
            Result.valueOf(data.getString(KEY_RESULT_CODE) ?: Result.Canceled.name)

        fun getUsername(data: Bundle): String = requireNotNull(data.getString(KEY_USERNAME))
        fun getPassword(data: Bundle): String = requireNotNull(data.getString(KEY_PASSWORD))
    }
}