package com.johnny.customfragmenttabhostlib

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TabHost
import android.widget.TabWidget
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

/**
 * @author Johnny
 */
class CustomFragmentTabHost(context: Context, attrs: AttributeSet?) : TabHost(context, attrs),
    TabHost.OnTabChangeListener {

    private val mTabs: MutableList<TabInfo> = mutableListOf()

    private var mRealTabContent: FrameLayout? = null
    private var mContainerId: Int = 0
    private var mContext: Context? = null
    private lateinit var mFragmentManager: FragmentManager
    private var mOnTabChangeListener: OnTabChangeListener? = null
    private var mLastTab: TabInfo? = null
    private var mAttached: Boolean = false

    private var mFragmentChangeType: FragmentChangeType = FragmentChangeType.SHOW_HIDE

    enum class FragmentChangeType {
        SHOW_HIDE,
        ATTACH_DETACH,
    }

    private data class TabInfo(
        val tag: String,
        val clazz: Class<*>,
        val args: Bundle?,
        var fragment: Fragment? = null
    )

    private class DummyTabFactory(private val context: Context) : TabContentFactory {

        override fun createTabContent(tag: String?): View = View(context).also {
            it.minimumWidth = 0
            it.minimumHeight = 0
        }
    }

    private class SavedState : BaseSavedState {

        var curTab: String? = null

        constructor(superState: Parcelable) : super(superState)

        constructor(inParcel: Parcel?) : super(inParcel) {
            curTab = inParcel?.readString()
        }

        override fun writeToParcel(out: Parcel?, flags: Int) {
            super.writeToParcel(out, flags)
            out?.writeString(this.curTab)
        }

        override fun toString(): String {
            return "FragmentTabHost.SavedState{${Integer.toHexString(System.identityHashCode(this))} curTab=${this.curTab}}"
        }

        override fun describeContents(): Int {
            return 0
        }

//        companion object {
//            @JvmField
//            val CREATOR:Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
//                override fun createFromParcel(source: Parcel): SavedState {
//                    return SavedState(source)
//                }
//
//                override fun newArray(size: Int): Array<SavedState?> {
//                    return arrayOfNulls(size)
//                }
//            }
//        }

        /* 这两种方式需要试验 */

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState {
                return SavedState(source)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    constructor(context: Context) : this(context, null)

    init {
        initFragmentTabHost(context, attrs)
    }

    private fun initFragmentTabHost(context: Context, attrs: AttributeSet?) {
        val ta = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.inflatedId), 0, 0)
        mContainerId = ta.getResourceId(0, 0)
        ta.recycle()

        super.setOnTabChangedListener(this)
    }

    private fun ensureHierarchy(context: Context) {
        if (findViewById<View>(android.R.id.tabs) == null) {
            val ll = LinearLayout(context)
            ll.orientation = LinearLayout.VERTICAL
            addView(
                ll,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            val tw = TabWidget(context)
            tw.id = android.R.id.tabs
            tw.orientation = TabWidget.HORIZONTAL
            ll.addView(
                tw,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0F
                )
            )

            var fl = FrameLayout(context)
            fl.id = android.R.id.tabcontent
            ll.addView(fl, LinearLayout.LayoutParams(0, 0, 0F))

            fl = FrameLayout(context)
            this.mRealTabContent = fl
            ll.addView(fl, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1F))
        }
    }

    private fun ensureContent() {
        if (this.mRealTabContent == null) {
            this.mRealTabContent = findViewById(mContainerId)
            if (this.mRealTabContent == null) {
                throw IllegalStateException("No tab content FrameLayout found for id $mContainerId")
            }
        }
    }

    fun setup(context: Context, manager: FragmentManager, containerId: Int) {
        ensureHierarchy(context)
        super.setup()
        this.mContext = context
        this.mFragmentManager = manager
        this.mContainerId = containerId
        ensureContent()
        this.mRealTabContent?.id = containerId

        if (id == View.NO_ID) {
            id = android.R.id.tabhost
        }
    }

    fun setup(context: Context, manager: FragmentManager) {
        ensureHierarchy(context)
        super.setup()
        this.mContext = context
        this.mFragmentManager = manager
        ensureContent()
    }

    override fun setup() {
        throw IllegalStateException("Must call setup() that takes a Context and FragmentManager")
    }

    override fun setOnTabChangedListener(l: OnTabChangeListener) {
        this.mOnTabChangeListener = l
    }

    fun addTab(tabSpec: TabSpec, clazz: Class<*>, args: Bundle?) {
        tabSpec.setContent(DummyTabFactory(mContext!!))

        val tag: String = tabSpec.tag
        val info = TabInfo(tag, clazz, args)

        if (this.mAttached) {
            // 初始化TabInfo中的fragment属性
            info.fragment = this.mFragmentManager.findFragmentByTag(tag)
            if (info.fragment != null) {
                val ft = this.mFragmentManager.beginTransaction()
                when (mFragmentChangeType) {
                    FragmentChangeType.SHOW_HIDE -> if (!info.fragment!!.isHidden) ft.hide(info.fragment!!)
                    FragmentChangeType.ATTACH_DETACH -> if (!info.fragment!!.isDetached) ft.detach(
                        info.fragment!!
                    )
                }
                ft.commit()
            }
        }

        this.mTabs.add(info)
        addTab(tabSpec)
    }

    fun setFragmentChangeType(changeType: FragmentChangeType) {
        this.mFragmentChangeType = changeType
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val currentTag = currentTabTag

        var ft: FragmentTransaction? = null
        for (index in this.mTabs.indices) {
            val tabInfo = this.mTabs[index]
            tabInfo.fragment = this.mFragmentManager.findFragmentByTag(tabInfo.tag)
            if (tabInfo.fragment != null) {
                if ((mFragmentChangeType == FragmentChangeType.SHOW_HIDE && !tabInfo.fragment!!.isHidden)
                    || (mFragmentChangeType == FragmentChangeType.ATTACH_DETACH && !tabInfo.fragment!!.isDetached)
                ) {
                    if (tabInfo.tag == currentTag) {
                        this.mLastTab = tabInfo
                    } else {
                        if (ft == null) {
                            ft = this.mFragmentManager.beginTransaction()
                        }
                        when (mFragmentChangeType) {
                            FragmentChangeType.SHOW_HIDE -> ft.hide(tabInfo.fragment!!)
                            FragmentChangeType.ATTACH_DETACH -> ft.detach(tabInfo.fragment!!)
                        }
                    }
                }
            }
        }

        this.mAttached = true
        ft = doTabChanged(currentTag, ft)
        if (ft != null) {
            ft.commit()
            this.mFragmentManager.executePendingTransactions()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        this.mAttached = false
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = superState?.let { SavedState(it) }
        ss?.curTab = currentTabTag
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        setCurrentTabByTag(state.curTab)
    }

    private fun doTabChanged(tag: String?, ft: FragmentTransaction?): FragmentTransaction? {
        val newTab = getTabInfoForTag(tag)
        var newFt: FragmentTransaction? = ft
        if (this.mLastTab != newTab) {
            if (newFt == null) {
                newFt = this.mFragmentManager.beginTransaction()
            }

            if (this.mLastTab != null) {
                if (this.mLastTab!!.fragment != null) {
                    when (mFragmentChangeType) {
                        FragmentChangeType.SHOW_HIDE -> newFt.hide(this.mLastTab!!.fragment!!)
                        FragmentChangeType.ATTACH_DETACH -> newFt.detach(this.mLastTab!!.fragment!!)
                    }
                }
            }

            if (newTab != null) {
                if (newTab.fragment == null) {
                    newTab.fragment = this.mFragmentManager.fragmentFactory.instantiate(
                        mContext!!.classLoader, newTab.clazz.name
                    )
                    newTab.fragment?.arguments = newTab.args
                    newFt.add(this.mContainerId, newTab.fragment!!, newTab.tag)
                } else {
                    when (mFragmentChangeType) {
                        FragmentChangeType.SHOW_HIDE -> newFt.show(newTab.fragment!!)
                        FragmentChangeType.ATTACH_DETACH -> newFt.attach(newTab.fragment!!)
                    }
                }
            }

            this.mLastTab = newTab
        }

        return newFt
    }

    override fun onTabChanged(tabId: String?) {
        if (this.mAttached) {
            val ft: FragmentTransaction? = doTabChanged(tabId, null)
            ft?.commit()
        }
        this.mOnTabChangeListener?.onTabChanged(tabId)
    }

    private fun getTabInfoForTag(tag: String?): TabInfo? {
        for (index in this.mTabs.indices) {
            val tabInfo = this.mTabs[index]
            if (tabInfo.tag == tag) {
                return tabInfo
            }
        }
        return null
    }
}