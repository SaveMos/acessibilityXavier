package it.unipi.dii.xavier

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.Button
import androidx.core.content.ContextCompat
import android.util.Log

class CustomKeyboardIME : InputMethodService() {

    private var keyboardView: View? = null
    private lateinit var inputConnection: InputConnection
    private var isCapsOn = false
    private var isEmojiOff = true
    private var defaultShiftBackground: Drawable? = null

    @SuppressLint("InflateParams")
    override fun onCreateInputView(): View {

        keyboardView = layoutInflater.inflate(R.layout.keyboard_start, null)

        val shiftButton = keyboardView?.findViewById<Button>(R.id.btn_maiusc)
        defaultShiftBackground = shiftButton?.background

        setupMainKeyboard()
        return keyboardView!!
    }

    private fun setupMainKeyboard() {
        inputConnection = currentInputConnection

        val abcButton = keyboardView?.findViewById<Button>(R.id.btn_letters_abc)
        val jklButton = keyboardView?.findViewById<Button>(R.id.btn_letters_jkl)
        val stuButton = keyboardView?.findViewById<Button>(R.id.btn_letters_stu)
        val numbersButton = keyboardView?.findViewById<Button>(R.id.btn_123)
        val crButton = keyboardView?.findViewById<Button>(R.id.btn_cr)
        val delButton = keyboardView?.findViewById<Button>(R.id.btn_cancel)
        val symbolsButton = keyboardView?.findViewById<Button>(R.id.btn_symbols)
        val openparButton = keyboardView?.findViewById<Button>(R.id.btn_par_open)
        val closeparButton = keyboardView?.findViewById<Button>(R.id.btn_par_close)

        abcButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_letters_abc) }
        jklButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_letters_jkl) }
        stuButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_letters_stu) }
        numbersButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_numbers) }
        crButton?.setOnClickListener { inputConnection.commitText("\n", 1) }
        delButton?.setOnClickListener {
            val before = inputConnection.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length >= 2 && Character.isSurrogatePair(before[0], before[1])) {
                // è una emoji “semplice” su due char
                inputConnection.deleteSurroundingText(2, 0)
            } else {
                // normale ascii o lettera
                inputConnection.deleteSurroundingText(1, 0)
            }
        }
        symbolsButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_symbols) }
        openparButton?.setOnClickListener { inputConnection.commitText("(", 1) }
        closeparButton?.setOnClickListener { inputConnection.commitText(")", 1) }
    }

    private fun showKeyboardLayout(layoutId: Int) {
        keyboardView = layoutInflater.inflate(layoutId, null)
        setInputView(keyboardView)
        inputConnection = currentInputConnection
        setupKeyboard()
    }

    private fun setupKeyboard() {
        // Scorri ricorsivamente tutti i View (inclusi i LinearLayout e Button) all'interno della tastiera
        @SuppressLint("InflateParams")
        fun setupButtonListeners(view: View?) {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    setupButtonListeners(view.getChildAt(i))
                }
            } else if (view is Button) {
                val buttonText = view.text?.toString()
                if (!buttonText.isNullOrEmpty()) {
                    view.setOnClickListener {
                        if(buttonText == "\uD83D\uDE0A" && isEmojiOff){
                            isEmojiOff = false
                            keyboardView = layoutInflater.inflate(R.layout.keyboard_emojis, null)
                            setInputView(keyboardView)
                            setupKeyboard()
                        }else {
                            val letter =
                                if (isCapsOn) buttonText.uppercase() else buttonText.lowercase()
                            inputConnection.commitText(letter, 1)
                            Log.d("CI ARRIVA QUA", "arriva qua")
                        }
                    }
                }
            }
        }

        setupButtonListeners(keyboardView)
        setCommonListeners()
    }

    private fun setCommonListeners() {
        keyboardView?.findViewById<Button>(R.id.btn_space)?.setOnClickListener {
            inputConnection.commitText(" ", 1)
        }
        keyboardView?.findViewById<Button>(R.id.btn_back)?.setOnClickListener {
            isEmojiOff = true
            showKeyboardLayout(R.layout.keyboard_start)
            setupMainKeyboard()
        }
        val shiftButton = keyboardView?.findViewById<Button>(R.id.btn_maiusc)

        shiftButton?.setOnClickListener {
            isCapsOn = !isCapsOn
            updateKeyTexts()
            if (isCapsOn) {
                shiftButton.setBackgroundColor(ContextCompat.getColor(this, R.color.shiftActiveBackground))
            } else {
                shiftButton.background = defaultShiftBackground

            }
        }
    }

    private fun updateKeyTexts() {
        fun updateButtons(view: View?) {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    updateButtons(view.getChildAt(i))
                }
            } else if (view is Button) {
                val text = view.text?.toString() ?: return
                if (text.length == 1 && text[0].isLetter()) {
                    view.text = if (isCapsOn) text.uppercase() else text.lowercase()
                }
            }
        }
        updateButtons(keyboardView)
    }
}