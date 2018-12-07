package com.Project3C.SensorTagGame;

/**
 * Interface for communication between MainActivity and fragments. Used for clicking on
 * list items as well as showing or hiding the progress indicator.
 */
public interface OnStatusListener {
    void onListFragmentInteraction(String address);

    void onShowProgress();

    void onHideProgress();
}
