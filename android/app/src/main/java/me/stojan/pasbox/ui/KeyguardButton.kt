package me.stojan.pasbox.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StringRes
import io.reactivex.disposables.CompositeDisposable
import me.stojan.pasbox.AppActivity
import me.stojan.pasbox.R
import me.stojan.pasbox.dev.use

class KeyguardButton @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : StateTextView(context, attrs, defStyleAttr) {
  companion object {
    private const val TRANSITION_INITIAL = 0
    private const val TRANSITION_FAILURE = 1
    private const val TRANSITION_SUCCESS = 2
  }

  interface Callbacks {
    fun onBegin(button: KeyguardButton) {
    }

    fun onSuccess(button: KeyguardButton) {
    }

    fun onFailure(button: KeyguardButton) {
    }
  }

  private val activity: AppActivity get() = context as AppActivity
  private val disposables = CompositeDisposable()

  @StringRes
  var title: Int = 0

  @StringRes
  var description: Int = 0

  var requestCode = RequestCodes.KEYGUARD_BUTTON_KEYGUARD

  var callbacks: Callbacks? = null

  init {
    context.obtainStyledAttributes(attrs, R.styleable.KeyguardButton)
      .use {
        title = getResourceId(R.styleable.KeyguardButton_title, 0)
        description = getResourceId(R.styleable.KeyguardButton_description, 0)

        transitions = intArrayOf(
          /* TRANSITION_INITIAL: */ 0, -1, getResourceId(R.styleable.KeyguardButton_initial, 0),
          /* TRANSITION_FAILURE: */ 2500, TRANSITION_INITIAL, getResourceId(R.styleable.KeyguardButton_failure, 0),
          /* TRANSITION_SUCCESS: */ 0, -1, getResourceId(R.styleable.KeyguardButton_success, 0)
        )
      }

    super.setOnClickListener {

      context.getSystemService(KeyguardManager::class.java)
        .let { keyguardManager ->
          disposables.add(activity.results.filter { requestCode == it.first }
            .take(1)
            .subscribe { (_, resultCode, _) ->
              when (resultCode) {
                Activity.RESULT_OK -> {
                  transition = TRANSITION_SUCCESS
                  callbacks?.onSuccess(this)
                }

                else -> {
                  transition = TRANSITION_FAILURE
                  callbacks?.onFailure(this)
                }
              }
            })

          activity.startActivityForResult(
            keyguardManager.createConfirmDeviceCredentialIntent(
              resources.getString(title), resources.getString(description)
            ), requestCode
          )

          callbacks?.onBegin(this)
        }
    }
  }

  override fun setOnClickListener(l: OnClickListener?) {
    throw RuntimeException("Setting click listener is not allowed")
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    disposables.dispose()
  }

}