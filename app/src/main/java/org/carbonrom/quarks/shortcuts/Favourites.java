package org.carbonrom.quarks.shortcuts;

import android.os.Bundle;

import org.carbonrom.quarks.MainActivity;
import org.carbonrom.quarks.R;

public class Favourites extends MainActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FavouritesShortcut();
    }
}