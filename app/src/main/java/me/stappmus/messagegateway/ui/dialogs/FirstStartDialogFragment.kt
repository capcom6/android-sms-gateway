package me.stappmus.messagegateway.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import me.stappmus.messagegateway.databinding.DialogFirstStartBinding

class FirstStartDialogFragment : DialogFragment() {

    private var _binding: DialogFirstStartBinding? = null

    // This property is only valid between onCreateDialog and
    // onDestroyView.
    private val binding get() = _binding!!
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogFirstStartBinding.inflate(layoutInflater)

        binding.tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    POSITION_SIGNUP -> {
                        binding.layoutSignUp.isVisible = true
                        binding.layoutSignIn.isVisible = false
                        binding.layoutSignInByCode.isVisible = false
                    }

                    POSITION_SIGNIN -> {
                        binding.layoutSignUp.isVisible = false
                        binding.layoutSignIn.isVisible = true
                        binding.layoutSignInByCode.isVisible = false
                    }

                    POSITION_SIGNIN_BY_CODE -> {
                        binding.layoutSignUp.isVisible = false
                        binding.layoutSignIn.isVisible = false
                        binding.layoutSignInByCode.isVisible = true
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        binding.buttonContinue.setOnClickListener {
            when (binding.tabLayout.selectedTabPosition) {
                POSITION_SIGNUP -> actionSignUp()
                POSITION_SIGNIN -> actionSignIn()
                POSITION_SIGNIN_BY_CODE -> actionSignInByCode()
            }
        }

        binding.buttonCancel.setOnClickListener {
            actionCancel()
        }

        return AlertDialog.Builder(requireActivity())
            .apply {
                setView(binding.root)
                setCancelable(false)
            }
            .create()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun actionSignUp() {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(KEY_RESULT_CODE, Result.SignUp.name)
            }
        )
        dismiss()
    }

    private fun actionSignIn() {
        val username = binding.editUsername.text.toString()
        val password = binding.editPassword.text.toString()

        binding.editUsernameLayout.error = null
        binding.editPasswordLayout.error = null

        if (username.isEmpty()) {
            binding.editUsernameLayout.error = "Required"
            return
        }

        if (password.isEmpty()) {
            binding.editPasswordLayout.error = "Required"
            return
        }

        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(KEY_RESULT_CODE, Result.SignIn.name)
                putString(KEY_USERNAME, username)
                putString(KEY_PASSWORD, password)
            }
        )
        dismiss()
    }

    private fun actionSignInByCode() {
        val code = binding.editCode.text.toString()

        binding.editCodeLayout.error = null

        if (code.isEmpty()) {
            binding.editCodeLayout.error = "Required"
            return
        }

        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(KEY_RESULT_CODE, Result.SignInByCode.name)
                putString(KEY_CODE, code)
            }
        )
        dismiss()
    }

    private fun actionCancel() {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(KEY_RESULT_CODE, Result.Canceled.name)
            }
        )
        dismiss()
    }

    enum class Result {
        Canceled,
        SignUp,
        SignIn,
        SignInByCode,
    }

    companion object {
        const val REQUEST_KEY = "FirstStartDialogFragment"

        private const val POSITION_SIGNUP = 0
        private const val POSITION_SIGNIN = 1
        private const val POSITION_SIGNIN_BY_CODE = 2

        private const val KEY_RESULT_CODE = "result_code"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_CODE = "code"

        fun newInstance(): FirstStartDialogFragment = FirstStartDialogFragment()

        fun getResult(data: Bundle): Result =
            Result.valueOf(data.getString(KEY_RESULT_CODE) ?: Result.Canceled.name)

        fun getUsername(data: Bundle): String = requireNotNull(data.getString(KEY_USERNAME))
        fun getPassword(data: Bundle): String = requireNotNull(data.getString(KEY_PASSWORD))

        fun getCode(data: Bundle): String = requireNotNull(data.getString(KEY_CODE))
    }
}