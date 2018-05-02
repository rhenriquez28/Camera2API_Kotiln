package henriquez.roy.com.camera2api_kotlin

import android.content.res.Configuration
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import henriquez.roy.com.camera2api_kotlin.Fragments.PreviewFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    //Simply, lazy creates an instance that performs initialization
    // at the first access to the property value, stores the result, and returns the stored value.
    // For reference: https://medium.com/til-kotlin/how-kotlins-delegated-properties-and-lazy-initialization-work-552cbad8be60
    val drawerToggle by lazy {
        //The last two parameters are descriptions used when the drawer is open or closed
        ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        {
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

        navigationView.setNavigationItemSelectedListener {
            selectDrawerItem(it)
            true
            //The true is to notify that we're consuming the listener event
            //No need notify when we're using the hybrid function "selectItem"
            //true
        }

        drawerLayout.addDrawerListener(drawerToggle)

        //Returns the object of PreviewFragment
        val fragment = PreviewFragment.newInstance()
        replaceFragment(fragment)

        //The View Pager is implemented once its fragments
        // have been implemented
        /*val pagerAdapter = FragmentPagerAdapter(supportFragmentManager)
        viewPager.adapter = pagerAdapter
        viewPager.addOnPageChangeListener(this)*/
    }

    //Function to call and manage the fragments
    private fun replaceFragment(fragment: android.support.v4.app.Fragment?){
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        //To replace the fragment it needs the id of the container
        // in this case being the Frame Layout of the Activity
        // and the fragement that is going to be replaced with
        fragmentTransaction.replace(fragmentContainer.id, fragment)
        fragmentTransaction.commit()
    }

    //Sync and configuration between the drawerToggle and drawerLayout
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }


    //Function used for Drawer _and_ the Bottom Navigation
    // For Drawer only the function needs to be named "selectDrawerItem"
    private fun selectDrawerItem(item: MenuItem): Boolean{
        when(item.itemId){
            R.id.firstFragmentItem -> viewPager.currentItem = 0
            /*R.id.secondFragmentItem -> viewPager.currentItem = 1
            R.id.thirdFragmentItem -> viewPager.currentItem = 2*/
            else -> viewPager.currentItem = 0
        }
        /*Used when the View Pager didn't exist
        var fragment: Fragment? = null
        //returns to us the class of the fragment requested to make an instance of the object
        val fragmentClass = when(item.itemId){
            R.id.firstFragmentItem -> PreviewFragment::class.java

            R.id.secondFragmentItem -> SecondImageFragment::class.java

            //Default fragment
            else -> PreviewFragment::class.java
        }
        //The "as" is to cast the object as a Fragment
        try {
            fragment = fragmentClass.newInstance() as Fragment
        }catch (e: ClassCastException){
            e.printStackTrace()
        }
        replaceFragment(fragment)*/

        //Checks if the drawer is open
        if (drawerLayout.isDrawerOpen(GravityCompat.START))
            //Closes the drawer
            drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    //Creates the menu in the Activity
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.fragment_menu, menu)
        return true
    }

    //Manages the actions of the activity when an item on the menu is selected
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        /*
        //Return is put here because expressions like "when" in Kotlin return a value
        return when(item?.itemId){
            R.id.firstFragmentItem -> {
                val fragment = PreviewFragment.newInstance()
                replaceFragment(fragment)
                true
            }

            R.id.secondFragmentItem -> {
                val fragment = SecondImageFragment.newInstance()
                replaceFragment(fragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }*/

        /*This one is supposed to be used when the drawerToggle is created
        but if this one is used you can no longer use the Options Menu to change fragments.
        The one from above also works with the toggle so I decided to keep the original.*/
        return if (drawerToggle.onOptionsItemSelected(item)){
            true
        }
        else {
            super.onOptionsItemSelected(item)
        }
    }


}
