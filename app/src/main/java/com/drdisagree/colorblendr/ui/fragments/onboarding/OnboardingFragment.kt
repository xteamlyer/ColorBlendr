package com.drdisagree.colorblendr.ui.fragments.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.drdisagree.colorblendr.ColorBlendr.Companion.appContext
import com.drdisagree.colorblendr.R
import com.drdisagree.colorblendr.data.common.Constant.WORKING_METHOD
import com.drdisagree.colorblendr.data.common.Utilities.setFirstRunCompleted
import com.drdisagree.colorblendr.data.common.Utilities.setWorkingMethod
import com.drdisagree.colorblendr.data.enums.WorkMethod
import com.drdisagree.colorblendr.databinding.FragmentOnboardingBinding
import com.drdisagree.colorblendr.provider.RootConnectionProvider
import com.drdisagree.colorblendr.provider.ShizukuConnectionProvider
import com.drdisagree.colorblendr.service.ShizukuConnection
import com.drdisagree.colorblendr.ui.activities.MainActivity
import com.drdisagree.colorblendr.ui.adapters.OnboardingAdapter
import com.drdisagree.colorblendr.ui.fragments.HomeFragment
import com.drdisagree.colorblendr.ui.fragments.onboarding.pages.OnboardingItem1Fragment
import com.drdisagree.colorblendr.ui.fragments.onboarding.pages.OnboardingItem2Fragment
import com.drdisagree.colorblendr.ui.fragments.onboarding.pages.OnboardingItem3Fragment
import com.drdisagree.colorblendr.ui.fragments.onboarding.pages.OnboardingItem4Fragment
import com.drdisagree.colorblendr.utils.app.AppUtil.permissionsGranted
import com.drdisagree.colorblendr.utils.fabricated.FabricatedUtil.updateFabricatedAppList
import com.drdisagree.colorblendr.utils.shizuku.ShizukuUtil.bindUserService
import com.drdisagree.colorblendr.utils.shizuku.ShizukuUtil.getUserServiceArgs
import com.drdisagree.colorblendr.utils.shizuku.ShizukuUtil.isShizukuAvailable
import com.drdisagree.colorblendr.utils.shizuku.ShizukuUtil.requestShizukuPermission
import com.drdisagree.colorblendr.utils.wallpaper.WallpaperColorUtil.updateWallpaperColorList
import com.drdisagree.colorblendr.utils.wifiadb.WifiAdbConnectedDevices
import kotlinx.coroutines.launch

class OnboardingFragment : Fragment() {

    private lateinit var binding: FragmentOnboardingBinding
    private lateinit var adapter: OnboardingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false)

        adapter = OnboardingAdapter(
            childFragmentManager,
            requireActivity().lifecycle
        )

        adapter.addFragment(OnboardingItem1Fragment())
        adapter.addFragment(OnboardingItem2Fragment())
        adapter.addFragment(OnboardingItem3Fragment())
        adapter.addFragment(OnboardingItem4Fragment())

        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = adapter.itemCount

        binding.viewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                animateBackButton(position)
                changeContinueButtonText(position)
            }
        })

        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem == adapter.itemCount - 1) {
                if (!permissionsGranted(requireContext())) {
                    Toast.makeText(
                        requireContext(),
                        R.string.grant_all_permissions,
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                when (WORKING_METHOD) {
                    WorkMethod.NULL -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.select_method,
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    WorkMethod.ROOT -> {
                        checkRootConnection()
                    }

                    WorkMethod.SHIZUKU -> {
                        checkShizukuConnection()
                    }

                    WorkMethod.WIRELESS_ADB -> {
                        checkAdbConnection()
                    }
                }

                return@setOnClickListener
            }

            binding.viewPager.currentItem += 1
        }

        binding.btnPrev.setOnClickListener {
            binding.viewPager.setCurrentItem(
                binding.viewPager.currentItem - 1, true
            )
        }

        registerOnBackInvokedCallback()

        return binding.root
    }

    private fun checkRootConnection() {
        RootConnectionProvider
            .builder(requireContext())
            .onSuccess { goToHomeFragment() }
            .run()
    }

    private fun checkShizukuConnection() {
        if (isShizukuAvailable) {
            requestShizukuPermission(requireActivity()) { granted: Boolean ->
                if (granted) {
                    bindUserService(
                        getUserServiceArgs(ShizukuConnection::class.java),
                        ShizukuConnectionProvider.serviceConnection
                    )
                    goToHomeFragment()
                } else {
                    Toast.makeText(
                        appContext,
                        R.string.shizuku_service_not_found,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                appContext,
                R.string.shizuku_service_not_found,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkAdbConnection() {
        if (WifiAdbConnectedDevices.isMyDeviceConnected()) {
            goToHomeFragment()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.wireless_adb_not_connected,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun goToHomeFragment() {
        lifecycleScope.launch {
            try {
                updateWallpaperColorList(requireContext())
                updateFabricatedAppList(requireContext())

                setFirstRunCompleted()
                setWorkingMethod(WORKING_METHOD)

                MainActivity.replaceFragment(
                    HomeFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("success", true)
                        }
                    },
                    true
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun animateBackButton(position: Int) {
        val duration = 300

        if (position == 0 && binding.btnPrev.isVisible) {
            val fadeOut = getFadeOutAnimation(duration)
            binding.btnPrev.startAnimation(fadeOut)
        } else if (position != 0 && binding.btnPrev.visibility != View.VISIBLE) {
            val fadeIn = getFadeInAnimation(duration)
            binding.btnPrev.startAnimation(fadeIn)
        }
    }

    @Suppress("SameParameterValue")
    private fun getFadeOutAnimation(duration: Int): AlphaAnimation {
        val fadeOut = AlphaAnimation(1.0f, 0.0f)

        fadeOut.duration = duration.toLong()

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
            }

            override fun onAnimationEnd(animation: Animation) {
                binding.btnPrev.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })

        return fadeOut
    }

    @Suppress("SameParameterValue")
    private fun getFadeInAnimation(duration: Int): AlphaAnimation {
        val fadeIn = AlphaAnimation(0.0f, 1.0f)

        fadeIn.duration = duration.toLong()

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                binding.btnPrev.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animation) {
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })

        return fadeIn
    }

    private fun changeContinueButtonText(position: Int) {
        if (position == adapter.itemCount - 1) {
            binding.btnNext.setText(R.string.start)
        } else {
            binding.btnNext.setText(R.string.btn_continue)
        }
    }

    private fun registerOnBackInvokedCallback() {
        requireActivity().onBackPressedDispatcher.addCallback(
            requireActivity(),
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    onBackPressed()
                }
            })
    }

    private fun onBackPressed() {
        val fragmentManager = requireActivity().supportFragmentManager

        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        } else if (binding.viewPager.currentItem == 0) {
            requireActivity().finish()
        } else {
            binding.viewPager.setCurrentItem(binding.viewPager.currentItem - 1, true)
        }
    }
}
