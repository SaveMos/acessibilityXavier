package it.unipi.dii.xavier

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.ImageButton

class CustomKeyboardIME : InputMethodService() {

    private var keyboardView: View? = null
    private var inputConnection: InputConnection? = null

    @SuppressLint("InflateParams")
    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_start, null)
        setupMainKeyboard()
        return keyboardView!!
    }

    private fun setupMainKeyboard() {
        inputConnection = currentInputConnection

        val abcButton = keyboardView?.findViewById<Button>(R.id.btn_letters_abc)
        val jklButton = keyboardView?.findViewById<Button>(R.id.btn_letters_jkl)
        val stuButton = keyboardView?.findViewById<Button>(R.id.btn_letters_stu)
        val symbolsButton = keyboardView?.findViewById<Button>(R.id.btnSymbols)
        val numbersButton = keyboardView?.findViewById<Button>(R.id.btn_123)

        abcButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_letters_abc) }
        jklButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_letters_jkl) }
        stuButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_letters_stu) }
        symbolsButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_symbols) }
        numbersButton?.setOnClickListener { showKeyboardLayout(R.layout.keyboard_numbers) }
    }

    private fun showKeyboardLayout(layoutId: Int) {
        keyboardView = layoutInflater.inflate(layoutId, null)
        setInputView(keyboardView)
        when (layoutId) {
            R.layout.keyboard_letters_abc -> setupABCKeyboard()
            R.layout.keyboard_letters_jkl -> setupJKLKeyboard()
            R.layout.keyboard_letters_stu -> setupSTUKeyboard()
            R.layout.keyboard_symbols -> setupSymbolsKeyboard()
            R.layout.keyboard_numbers -> setupNumbersKeyboard()
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun setupABCKeyboard() {
        val keys = listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I')
        for (char in keys) {
            val resId = resources.getIdentifier("btn$char", "id", packageName)
            keyboardView?.findViewById<Button>(resId)?.setOnClickListener {
                inputConnection?.commitText(char.toString(), 1)
            }
        }
        setCommonListeners()
    }

    @SuppressLint("DiscouragedApi")
    private fun setupJKLKeyboard() {
        val keys = listOf('J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R')
        for (char in keys) {
            val resId = resources.getIdentifier("btn$char", "id", packageName)
            keyboardView?.findViewById<Button>(resId)?.setOnClickListener {
                inputConnection?.commitText(char.toString(), 1)
            }
        }
        setCommonListeners()
    }

    @SuppressLint("DiscouragedApi")
    private fun setupSTUKeyboard() {
        val keys = listOf('S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z')
        for (char in keys) {
            val resId = resources.getIdentifier("btn$char", "id", packageName)
            keyboardView?.findViewById<Button>(resId)?.setOnClickListener {
                inputConnection?.commitText(char.toString(), 1)
            }
        }
        keyboardView?.findViewById<ImageButton>(R.id.btnEmoji)?.setOnClickListener {
            // Emojis non implementati
        }
        setCommonListeners()
    }

    @SuppressLint("DiscouragedApi")
    private fun setupSymbolsKeyboard() {
        val symbols = listOf('!', '.', ';', '?', ':', '-', '\'', '"', '@', ',')
        for (symbol in symbols) {
            val resId = resources.getIdentifier("btn${symbol.toString().replace("'", "Apostrophe")}", "id", packageName)
            keyboardView?.findViewById<Button>(resId)?.setOnClickListener {
                inputConnection?.commitText(symbol.toString(), 1)
            }
        }
        setCommonListeners()
    }

    @SuppressLint("DiscouragedApi")
    private fun setupNumbersKeyboard() {
        val numbers = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        for (number in numbers) {
            val resId = resources.getIdentifier("btn$number", "id", packageName)
            keyboardView?.findViewById<Button>(resId)?.setOnClickListener {
                inputConnection?.commitText(number.toString(), 1)
            }
        }
        setCommonListeners()
    }


    private fun setCommonListeners() {
        keyboardView?.findViewById<Button>(R.id.btnSpace)?.setOnClickListener {
            inputConnection?.commitText(" ", 1)
        }
        keyboardView?.findViewById<Button>(R.id.btnBack)?.setOnClickListener {
            showKeyboardLayout(R.layout.keyboard_start)
        }
        keyboardView?.findViewById<Button>(R.id.btnShift)?.setOnClickListener {
            // Shift toggling not implemented
        }
    }

}