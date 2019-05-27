package me.stojan.pasbox.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.android.material.textfield.TextInputLayout
import me.stojan.pasbox.totp.TOTP

class OTPView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  init {
    orientation = VERTICAL
  }

  private var _totp: TOTP? = null
  var totp: TOTP?
    get() = _totp
    set(value) {
      removeCallbacks(callback)
      _totp = value

      if (childCount > 0) {
        display()
      }
    }

  private val callback = object : Runnable {
    override fun run() {
      display()
    }
  }

  private lateinit var otpLayout: TextInputLayout
  private lateinit var progress: ProgressBar
  private var progressAnimator: ObjectAnimator? = null

  override fun onFinishInflate() {
    super.onFinishInflate()

    for (i in 0 until childCount) {
      getChildAt(i).let { child ->
        when (child) {
          is TextInputLayout -> otpLayout = child
          is ProgressBar -> progress = child
        }
      }
    }

    display()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    if (!isInEditMode) {
      display()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    removeCallbacks(callback)
    progressAnimator?.cancel()

    progressAnimator = null
  }

  private fun display() {
    totp?.let { totp ->
      val now = System.currentTimeMillis()
      val nowStep = now % totp.step
      val remaining = totp.step - nowStep

      progress.max = (totp.step * 4L).toInt()
      otpLayout.editText!!.setText(String(totp.atTime(now)))

      postDelayed(callback, remaining)

      if (null != progressAnimator) {
        progressAnimator!!.cancel()
      }

      progressAnimator =
        ObjectAnimator.ofInt(progress, "progress", (nowStep.toInt() * 4L).toInt(), (totp.step * 4L).toInt())
          .apply {
            duration = remaining
            interpolator = DecelerateInterpolator()
          }

      progressAnimator!!.start()
    }
  }
}
