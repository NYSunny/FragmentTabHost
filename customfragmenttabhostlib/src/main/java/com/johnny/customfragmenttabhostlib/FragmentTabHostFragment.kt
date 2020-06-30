package com.johnny.customfragmenttabhostlib

import androidx.fragment.app.Fragment

/**
 * @author Johnny
 */
open class FragmentTabHostFragment : Fragment() {

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) onHide()
        else onShow()
    }

    open fun onHide() {
        // empty impl
    }

    open fun onShow() {
        // empty impl
    }
}