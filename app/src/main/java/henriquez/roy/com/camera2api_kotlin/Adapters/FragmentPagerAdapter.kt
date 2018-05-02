package henriquez.roy.com.camera2api_kotlin.Adapters

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import henriquez.roy.com.camera2api_kotlin.Fragments.PreviewFragment

class FragmentPagerAdapter(fragmentManager: FragmentManager): FragmentPagerAdapter(fragmentManager) {
    //Function to get the fragment we want to get on the swipe
    override fun getItem(position: Int): Fragment {

        return when(position){
            0 -> PreviewFragment.newInstance()
            else -> PreviewFragment.newInstance()
        }
    }

    //Function to know the number of fragments the pager is dealing with
    override fun getCount() = 1


}