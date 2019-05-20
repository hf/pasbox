package me.stojan.pasbox.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import me.stojan.pasbox.R

class UISecretOTP @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : UISecret(context, attrs, defStyleAttr) {


  private lateinit var title: TextView

  override fun onFinishInflate() {
    super.onFinishInflate()

    title = findViewById(R.id.title)
  }

  override fun onBind() {
    super.onBind()

    this.title.text = public.otp.title
  }
}