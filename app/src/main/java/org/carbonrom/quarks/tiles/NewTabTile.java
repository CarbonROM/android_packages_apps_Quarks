package org.carbonrom.quarks.tiles;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import org.carbonrom.quarks.MainActivity;

@TargetApi(Build.VERSION_CODES.N)
public class NewTabTile extends TileService {

    @Override
    public void onClick() {
        Intent collapseIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(collapseIntent);
        Context context = getApplicationContext();
        MainActivity.handleShortcuts(context, "newtab");
    }
}