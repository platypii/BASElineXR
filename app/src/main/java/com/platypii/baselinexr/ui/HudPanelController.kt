
package com.platypii.baselinexr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import com.platypii.baselinexr.ui.wind.WindEstimationController
import com.platypii.baselinexr.wind.WindDataPoint
import com.platypii.baselinexr.wind.WindSystem

/**
 * Main controller for the HUD panel UI with modular menu system
 */
class HudPanelController(private val activity: BaselineActivity) {

    // Menu states
    private enum class MenuState { CLOSED, MAIN_MENU, WIND_MENU, POLARS_MENU, SETTINGS_MENU }
    private var currentMenuState = MenuState.CLOSED

    // Wind input source selection
    private enum class WindInputSource { ESTIMATION, NO_WIND, SAVED }
    private var windInputSource: WindInputSource = WindInputSource.ESTIMATION

    private val windEstimationController = WindEstimationController(activity)
    private val headingController = HeadingController(activity)
    private var polarsController: PolarsController? = null

    private var rootView: View? = null
    private var menuContainer: FrameLayout? = null
    private var currentMenuView: View? = null

    // Track if controllers have been initialized
    private var windControllerInitialized = false

    fun setupPanel(rootView: View?) {
        this.rootView = rootView
        this.menuContainer = rootView?.findViewById(R.id.menuContainer)

        android.util.Log.d("BXRINPUT", "HudPanelController.setupPanel() called - rootView=$rootView")

        // Get references to views
        val exitButton = rootView?.findViewById<Button>(R.id.exit_button)
        val headingButton = rootView?.findViewById<Button>(R.id.heading_button)
        val hudPanel = rootView?.findViewById<View>(R.id.hudPanel)

        // Set up touch listener on root that manually dispatches based on coordinates
        // This is necessary because Meta Spatial SDK may not properly dispatch to child views
        rootView?.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                android.util.Log.i("BXRINPUT", "ROOT TOUCH at ($x, $y)")

                // Check if touch is on exit button
                if (exitButton != null && isPointInView(exitButton, x, y, rootView)) {
                    android.util.Log.i("BXRINPUT", "Touch is on EXIT BUTTON - finishing activity")
                    activity.finish()
                    return@setOnTouchListener true
                }

                // Check if touch is on heading button (when visible)
                if (headingButton != null && headingButton.visibility == View.VISIBLE && isPointInView(headingButton, x, y, rootView)) {
                    android.util.Log.i("BXRINPUT", "Touch is on HEADING BUTTON - showing main menu")
                    showMenu(MenuState.MAIN_MENU)
                    return@setOnTouchListener true
                }

                // Check if touch is on menu container (when visible)
                if (menuContainer != null && menuContainer?.visibility == View.VISIBLE && isPointInView(menuContainer!!, x, y, rootView)) {
                    android.util.Log.i("BXRINPUT", "Touch is in MENU CONTAINER area at ($x, $y)")
                    // Let the menu handle its own clicks - dispatch to child
                    dispatchTouchToMenu(event, x, y)
                    return@setOnTouchListener true
                }

                // Check if touch is on hudPanel
                if (hudPanel != null && isPointInView(hudPanel, x, y, rootView)) {
                    android.util.Log.i("BXRINPUT", "Touch is on HUD PANEL - toggling main menu")
                    toggleMainMenu()
                    return@setOnTouchListener true
                }

                android.util.Log.d("BXRINPUT", "Touch at ($x, $y) did not hit any known view")
            }
            true
        }

        // Set up HUD references
        val latlngLabel = rootView?.findViewById<TextView>(R.id.lat_lng)
        val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
        activity.hudSystem?.setLabels(latlngLabel, speedLabel)

        android.util.Log.d("HudPanelController", "Menu system initialized")
    }

    /**
     * Check if a point is within a view's bounds relative to a parent
     */
    private fun isPointInView(view: View, x: Int, y: Int, parent: View): Boolean {
        val location = IntArray(2)
        val parentLocation = IntArray(2)
        view.getLocationInWindow(location)
        parent.getLocationInWindow(parentLocation)
        
        val relX = location[0] - parentLocation[0]
        val relY = location[1] - parentLocation[1]
        
        return x >= relX && x <= relX + view.width &&
               y >= relY && y <= relY + view.height
    }

    /**
     * Dispatch touch event to menu buttons
     */
    private fun dispatchTouchToMenu(event: android.view.MotionEvent, x: Int, y: Int) {
        val menuView = currentMenuView ?: return
        
        // Find all buttons in menu and check if touch hits them
        findClickableViews(menuView).forEach { button ->
            if (isPointInView(button, x, y, rootView!!)) {
                android.util.Log.i("BXRINPUT", "Menu button hit: ${button.javaClass.simpleName} id=${button.id}")
                button.performClick()
                return
            }
        }
    }

    /**
     * Recursively find all clickable views (buttons) in a view hierarchy
     */
    private fun findClickableViews(view: View): List<View> {
        val result = mutableListOf<View>()
        if (view is Button || (view.isClickable && view !is android.view.ViewGroup)) {
            result.add(view)
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(findClickableViews(view.getChildAt(i)))
            }
        }
        return result
    }

    /**
     * Public method called from HudSystem InputListener when header is clicked
     */
    fun handleHeaderClick() {
        android.util.Log.i("BXRINPUT", "handleHeaderClick() called from InputListener")
        toggleMainMenu()
    }
    
    /**
     * Public method called from HudSystem InputListener when heading button is clicked
     * Heading button is visible when in a sub-menu and returns to main menu
     */
    fun handleHeadingButtonClick() {
        android.util.Log.i("BXRINPUT", "handleHeadingButtonClick() called from InputListener - currentState=$currentMenuState")
        // If in a sub-menu, go back to main menu
        if (currentMenuState in listOf(MenuState.WIND_MENU, MenuState.POLARS_MENU, MenuState.SETTINGS_MENU)) {
            showMenu(MenuState.MAIN_MENU)
        }
    }
    
    /**
     * Public method called from HudSystem InputListener when menu area is clicked
     * @param u Normalized X coordinate (0-1, left to right)
     * @param v Normalized Y coordinate (0-1, top to bottom in view coords)
     */
    fun handleMenuClick(u: Float, v: Float) {
        android.util.Log.i("BXRINPUT", "handleMenuClick($u, $v) called from InputListener, menuState=$currentMenuState")
        
        if (currentMenuState == MenuState.CLOSED) {
            android.util.Log.d("BXRINPUT", "Menu is closed, ignoring click")
            return
        }
        
        // Convert normalized UV to pixel coordinates within the panel
        // The panel view dimensions tell us the actual pixel size
        val panelView = rootView ?: return
        val panelWidth = panelView.width.toFloat()
        val panelHeight = panelView.height.toFloat()
        
        if (panelWidth <= 0 || panelHeight <= 0) {
            android.util.Log.w("BXRINPUT", "Panel has invalid dimensions: ${panelWidth}x${panelHeight}")
            return
        }
        
        val pixelX = (u * panelWidth).toInt()
        val pixelY = (v * panelHeight).toInt()
        
        android.util.Log.i("BXRINPUT", "Converted to pixels: ($pixelX, $pixelY) in ${panelWidth.toInt()}x${panelHeight.toInt()} panel")
        
        // Try to find and click the button at these pixel coordinates
        // First search in the current menu view
        val menuView = currentMenuView
        if (menuView != null) {
            android.util.Log.d("BXRINPUT", "Searching in menuView: ${menuView.javaClass.simpleName}")
            val clickedView = findViewAtPosition(menuView, pixelX, pixelY, panelView)
            if (clickedView != null) {
                android.util.Log.i("BXRINPUT", "Found clickable view: ${clickedView.javaClass.simpleName} id=${clickedView.id}")
                // Use callOnClick() which directly invokes the OnClickListener
                // performClick() may not always trigger the listener
                val clicked = clickedView.callOnClick()
                android.util.Log.i("BXRINPUT", "callOnClick() returned: $clicked")
                if (clicked) return
                // If callOnClick returned false (no listener), try performClick as fallback
                clickedView.performClick()
                return
            } else {
                android.util.Log.d("BXRINPUT", "No clickable view found at ($pixelX, $pixelY)")
                // Log all buttons and their positions for debugging
                logAllButtonPositions(menuView, panelView)
            }
        }
        
        // Fallback to grid-based handling for main menu
        handleMenuClickByGrid(u, v)
    }
    
    /**
     * Log all button positions for debugging
     */
    private fun logAllButtonPositions(view: View, rootView: View) {
        val buttons = findClickableViews(view)
        val rootLocation = IntArray(2)
        rootView.getLocationInWindow(rootLocation)
        
        android.util.Log.d("BXRINPUT", "=== Button positions (${buttons.size} buttons found) ===")
        buttons.take(10).forEach { button ->
            val loc = IntArray(2)
            button.getLocationInWindow(loc)
            val relX = loc[0] - rootLocation[0]
            val relY = loc[1] - rootLocation[1]
            val text = if (button is Button) button.text else "N/A"
            android.util.Log.d("BXRINPUT", "  Button '$text': ($relX, $relY) size=${button.width}x${button.height} visible=${button.visibility == View.VISIBLE}")
        }
    }
    
    /**
     * Find a clickable view at the given pixel position
     */
    private fun findViewAtPosition(view: View, x: Int, y: Int, rootView: View): View? {
        // Check if this view contains the point
        if (!isPointInView(view, x, y, rootView)) {
            return null
        }
        
        // If it's a ViewGroup, check children first (depth-first)
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val result = findViewAtPosition(child, x, y, rootView)
                if (result != null) {
                    return result
                }
            }
        }
        
        // If this view is clickable (button), return it
        if (view is Button || (view.isClickable && view.visibility == View.VISIBLE)) {
            return view
        }
        
        return null
    }
    
    /**
     * Fallback grid-based menu click handling (for main menu)
     */
    private fun handleMenuClickByGrid(u: Float, v: Float) {
        // Menu grid layout (heading.xml):
        // - 3 columns x 4 rows
        // - Row 0: Wind, Polars, Settings (80dp height)
        // - Rows 1-3: Direction buttons (100dp height each)
        // - Total menu height: ~380dp (80 + 100*3)
        // - Button width: ~104dp each (100dp + margins)
        // - Total menu width: ~312dp
        
        // Menu is centered horizontally, so we need to find menu bounds
        // The menu starts at v=0.20 (below header which is 20% of panel)
        // Panel aspect ratio 4:3 means if width is ~600dp, height is ~450dp when menu shown
        // Menu area occupies v from 0.20 to roughly 0.20 + 380/450 ≈ 0.20 to 1.0
        
        val menuTop = 0.20f  // Menu starts below header
        val menuV = (v - menuTop) / (1.0f - menuTop)  // Normalize v within menu area (0-1)
        
        // Menu grid is centered, so we need to adjust u as well
        // Menu is ~312dp wide, panel is ~600dp wide
        // Menu starts at (600-312)/2 = 144dp from left = 144/600 ≈ 0.24
        val menuLeftU = 0.24f
        val menuRightU = 0.76f
        val menuWidth = menuRightU - menuLeftU
        
        // Clamp u to menu area
        val menuU = if (u < menuLeftU || u > menuRightU) {
            android.util.Log.d("BXRINPUT", "Click outside menu width bounds")
            return
        } else {
            (u - menuLeftU) / menuWidth  // Normalize u within menu (0-1)
        }
        
        // Calculate row based on menu v:
        // Row 0 (menu buttons): 0.0 to ~0.21 (80/380)
        // Row 1: 0.21 to ~0.47 (100/380)
        // Row 2: 0.47 to ~0.74 (100/380)
        // Row 3: 0.74 to 1.0 (100/380)
        val row = when {
            menuV < 0.0f -> -1  // Above menu
            menuV < 0.21f -> 0  // Wind/Polars/Settings row
            menuV < 0.47f -> 1  // Tail/N/Nose row
            menuV < 0.74f -> 2  // W/Center/E row
            menuV <= 1.0f -> 3  // -5°/S/+5° row
            else -> -1  // Below menu
        }
        
        // Calculate column (3 columns)
        val col = (menuU * 3).toInt().coerceIn(0, 2)
        
        android.util.Log.i("BXRINPUT", "Grid menu click: row=$row, col=$col (menuV=$menuV, menuU=$menuU)")
        
        when (currentMenuState) {
            MenuState.MAIN_MENU -> {
                if (row == -1) {
                    android.util.Log.d("BXRINPUT", "Click outside menu bounds")
                    return
                }
                // Row 0: Wind, Polars, Settings buttons
                if (row == 0) {
                    when (col) {
                        0 -> {
                            android.util.Log.i("BXRINPUT", "WIND menu button clicked")
                            showMenu(MenuState.WIND_MENU)
                        }
                        1 -> {
                            android.util.Log.i("BXRINPUT", "POLARS menu button clicked")
                            showMenu(MenuState.POLARS_MENU)
                        }
                        2 -> {
                            android.util.Log.i("BXRINPUT", "SETTINGS menu button clicked")
                            showMenu(MenuState.SETTINGS_MENU)
                        }
                    }
                } else if (row in 1..3) {
                    // Direction buttons in rows 1-3
                    handleDirectionButton(row, col)
                }
            }
            MenuState.WIND_MENU, MenuState.POLARS_MENU, MenuState.SETTINGS_MENU -> {
                // Sub-menus handled by findViewAtPosition above
                android.util.Log.d("BXRINPUT", "Sub-menu click not handled by grid fallback")
            }
            else -> {}
        }
    }
    
    private fun handleDirectionButton(row: Int, col: Int) {
        // Direction button grid (rows 1-3 of main menu, after row 0 which is Wind/Polars/Settings)
        // Row 1: Tail, N, Nose
        // Row 2: W, Center, E
        // Row 3: -5°, S, +5°
        android.util.Log.i("BXRINPUT", "Direction button: row=$row, col=$col")
        
        // Get the corresponding button and click it
        val buttonId = when (row to col) {
            1 to 0 -> R.id.tail_button
            1 to 1 -> R.id.north_button
            1 to 2 -> R.id.fwd_button
            2 to 0 -> R.id.west_button
            2 to 1 -> R.id.center_button
            2 to 2 -> R.id.east_button
            3 to 0 -> R.id.yaw_minus_button
            3 to 1 -> R.id.south_button
            3 to 2 -> R.id.yaw_plus_button
            else -> null
        }
        
        buttonId?.let {
            currentMenuView?.findViewById<Button>(it)?.performClick()
        }
    }

    /**
     * Toggle main menu on/off when clicking top bar
     */
    private fun toggleMainMenu() {
        android.util.Log.d("BXRINPUT", "toggleMainMenu() - currentMenuState=$currentMenuState")
        when (currentMenuState) {
            MenuState.CLOSED -> {
                android.util.Log.i("BXRINPUT", "Opening main menu")
                showMenu(MenuState.MAIN_MENU)
            }
            else -> {
                android.util.Log.i("BXRINPUT", "Closing all menus")
                closeAllMenus()
            }
        }
    }

    /**
     * Show a specific menu
     */
    private fun showMenu(menuState: MenuState) {
        android.util.Log.i("BXRINPUT", "showMenu() - showing $menuState")

        // Clean up previous menu
        currentMenuView?.let {
            android.util.Log.d("BXRINPUT", "Removing previous menu view")
            menuContainer?.removeView(it)
        }
        currentMenuView = null

        val inflater = LayoutInflater.from(activity)

        when (menuState) {
            MenuState.MAIN_MENU -> {
                android.util.Log.d("BXRINPUT", "Inflating heading.xml for main menu")
                currentMenuView = inflater.inflate(R.layout.heading, menuContainer, false)
                setupMainMenuButtons(currentMenuView)
                headingController.setupControls(currentMenuView)
            }
            MenuState.WIND_MENU -> {
                android.util.Log.d("BXRINPUT", "Inflating wind.xml for wind menu")
                currentMenuView = inflater.inflate(R.layout.wind, menuContainer, false)
                setupWindMenuButtons(currentMenuView)
                // Initialize or re-initialize wind controller with the current view
                if (!windControllerInitialized) {
                    windEstimationController.initialize(currentMenuView)
                    windControllerInitialized = true
                } else {
                    // Re-setup UI with new view when returning to wind menu
                    windEstimationController.setupWindEstimationUI(currentMenuView)
                }
                windEstimationController.startCollection()
            }
            MenuState.POLARS_MENU -> {
                currentMenuView = inflater.inflate(R.layout.polars, menuContainer, false)
                // Initialize polars controller
                currentMenuView?.let { view ->
                    polarsController = PolarsController(view)
                    polarsController?.setupPolarUI()
                }
            }
            MenuState.SETTINGS_MENU -> {
                currentMenuView = inflater.inflate(R.layout.settings, menuContainer, false)
            }
            MenuState.CLOSED -> {
                menuContainer?.visibility = View.GONE
                return
            }
        }

        // Add new menu to container and show
        currentMenuView?.let {
            android.util.Log.d("BXRINPUT", "Adding menu view to container, setting visible")

            // Recursively disable click interception on all container views
            // This prevents transparent layouts from blocking touches to underlying buttons
            disableClickInterceptionRecursive(it)
            android.util.Log.d("BXRINPUT", "Disabled click interception on menu view hierarchy")

            menuContainer?.addView(it)
            menuContainer?.visibility = View.VISIBLE
        }

        currentMenuState = menuState
        android.util.Log.i("BXRINPUT", "Menu state updated to: $menuState")

        // Scale panel larger for better visibility
        activity.hudSystem?.setExtraControlsVisible(true)

        // Show heading button for sub-menus, hide for main menu
        val headingButton = rootView?.findViewById<Button>(R.id.heading_button)
        headingButton?.visibility = if (menuState in listOf(MenuState.WIND_MENU, MenuState.POLARS_MENU, MenuState.SETTINGS_MENU)) {
            View.VISIBLE
        } else {
            View.GONE
        }

        android.util.Log.d("HudPanelController", "Showing menu: $menuState")
    }

    /**
     * Recursively disable click interception on container views (but not on buttons).
     * This allows touches to pass through transparent layout containers to underlying UI elements.
     */
    private fun disableClickInterceptionRecursive(view: View) {
        // Only disable click interception on container views, not on interactive elements like buttons
        if (view is ViewGroup && view !is Button) {
            view.isClickable = false
            view.isFocusable = false
            android.util.Log.v("BXRINPUT", "Disabled clicks on: ${view.javaClass.simpleName} id=${view.id}")

            // Recursively process all children
            for (i in 0 until view.childCount) {
                disableClickInterceptionRecursive(view.getChildAt(i))
            }
        }
    }

    /**
     * Close all menus
     */
    private fun closeAllMenus() {
        android.util.Log.i("BXRINPUT", "closeAllMenus() called - currentMenuState=$currentMenuState")

        if (currentMenuState == MenuState.WIND_MENU) {
            windEstimationController.stopCollection()
        }

        currentMenuView?.let {
            android.util.Log.d("BXRINPUT", "Removing menu view and hiding container")
            menuContainer?.removeView(it)
        }
        currentMenuView = null
        menuContainer?.visibility = View.GONE
        currentMenuState = MenuState.CLOSED
        android.util.Log.i("BXRINPUT", "All menus closed, state=$currentMenuState")

        // Scale panel back to normal size
        activity.hudSystem?.setExtraControlsVisible(false)

        // Hide heading button when menus are closed
        val headingButton = rootView?.findViewById<Button>(R.id.heading_button)
        headingButton?.visibility = View.GONE

        android.util.Log.d("HudPanelController", "All menus closed")
    }

    /**
     * Setup button listeners for main menu
     */
    private fun setupMainMenuButtons(view: View?) {
        view?.findViewById<Button>(R.id.wind_menu_button)?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Wind menu button clicked")
            showMenu(MenuState.WIND_MENU)
        }

        view?.findViewById<Button>(R.id.polars_menu_button)?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Polars menu button clicked")
            showMenu(MenuState.POLARS_MENU)
        }

        view?.findViewById<Button>(R.id.settings_menu_button)?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Settings menu button clicked")
            showMenu(MenuState.SETTINGS_MENU)
        }
    }

    /**
     * Setup button listeners for wind menu
     */
    private fun setupWindMenuButtons(view: View?) {
        // Wind input source buttons (act like radio buttons)
        val windEstimationButton = view?.findViewById<Button>(R.id.wind_input_estimation)
        val windNoWindButton = view?.findViewById<Button>(R.id.wind_input_nowind)
        val windSavedButton = view?.findViewById<Button>(R.id.wind_input_saved)

        windEstimationButton?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Wind Estimation button clicked!")
            setWindInputSource(WindInputSource.ESTIMATION)
            updateWindDisplayVisibility(view, WindInputSource.ESTIMATION)
            updateButtonSelection(windEstimationButton, windNoWindButton, windSavedButton)
        }

        windNoWindButton?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "No Wind button clicked!")
            setWindInputSource(WindInputSource.NO_WIND)
            updateWindDisplayVisibility(view, WindInputSource.NO_WIND)
            updateButtonSelection(windNoWindButton, windEstimationButton, windSavedButton)
        }

        windSavedButton?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Saved Wind button clicked!")
            setWindInputSource(WindInputSource.SAVED)
            updateWindDisplayVisibility(view, WindInputSource.SAVED)
            updateButtonSelection(windSavedButton, windEstimationButton, windNoWindButton)
        }

        // Set initial state to Wind Estimation
        setWindInputSource(WindInputSource.ESTIMATION)
        updateWindDisplayVisibility(view, WindInputSource.ESTIMATION)
        updateButtonSelection(windEstimationButton, windNoWindButton, windSavedButton)
    }

    /**
     * Update button selection to show which one is active
     */
    private fun updateButtonSelection(selectedButton: Button?, vararg otherButtons: Button?) {
        selectedButton?.alpha = 1.0f
        otherButtons.forEach { it?.alpha = 0.5f }
    }

    /**
     * Update visibility of wind displays based on selected mode
     */
    private fun updateWindDisplayVisibility(view: View?, source: WindInputSource) {
        val estimationDisplay = view?.findViewById<android.widget.LinearLayout>(R.id.wind_estimation_display)
        val savedDisplay = view?.findViewById<android.widget.LinearLayout>(R.id.wind_saved_display)

        when (source) {
            WindInputSource.ESTIMATION -> {
                estimationDisplay?.visibility = View.VISIBLE
                savedDisplay?.visibility = View.GONE
            }
            WindInputSource.SAVED -> {
                estimationDisplay?.visibility = View.GONE
                savedDisplay?.visibility = View.VISIBLE
            }
            WindInputSource.NO_WIND -> {
                estimationDisplay?.visibility = View.GONE
                savedDisplay?.visibility = View.GONE
            }
        }
    }

    /**
     * Add new data point to wind layers
     */
    fun addDataPointToLayers(dataPoint: WindDataPoint) {
        windEstimationController.addDataPointToLayers(dataPoint)
    }

    /**
     * Set wind input source and update WindSystem mode
     */
    private fun setWindInputSource(source: WindInputSource) {
        windInputSource = source
        val windSystem = WindSystem.getInstance()
        when (source) {
            WindInputSource.ESTIMATION -> windSystem.setWindMode(WindSystem.WindMode.ESTIMATION)
            WindInputSource.NO_WIND -> windSystem.setWindMode(WindSystem.WindMode.NO_WIND)
            WindInputSource.SAVED -> windSystem.setWindMode(WindSystem.WindMode.SAVED)
        }
    }

    fun cleanup() {
        windEstimationController.cleanup()
    }
}