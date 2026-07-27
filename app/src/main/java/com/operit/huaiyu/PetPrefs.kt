package com.operit.huaiyu

import android.content.Context
import android.content.SharedPreferences

object PetPrefs {
    private const val PREF_NAME = "huaiyu_pet_prefs"
    private const val KEY_SIZE = "pet_size"
    private const val KEY_CUSTOM_IMAGE_URI = "custom_image_uri"
    private const val KEY_USE_CUSTOM_IMAGE = "use_custom_image"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getPetSize(context: Context): Int {
        return prefs(context).getInt(KEY_SIZE, 100)
    }

    fun setPetSize(context: Context, sizeDp: Int) {
        prefs(context).edit().putInt(KEY_SIZE, sizeDp).apply()
    }

    fun getCustomImageUri(context: Context): String? {
        return prefs(context).getString(KEY_CUSTOM_IMAGE_URI, null)
    }

    fun setCustomImageUri(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_CUSTOM_IMAGE_URI, uri).apply()
    }

    fun useCustomImage(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_USE_CUSTOM_IMAGE, false)
    }

    fun setUseCustomImage(context: Context, use: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_CUSTOM_IMAGE, use).apply()
    }
}
