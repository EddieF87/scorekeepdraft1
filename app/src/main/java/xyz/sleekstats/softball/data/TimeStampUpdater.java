package xyz.sleekstats.softball.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

import static xyz.sleekstats.softball.data.FirestoreHelper.LAST_UPDATE;
import static xyz.sleekstats.softball.data.FirestoreHelper.LEAGUE_COLLECTION;
import static xyz.sleekstats.softball.data.FirestoreHelper.UPDATE_SETTINGS;

public class TimeStampUpdater {

    //TIMESTAMP MAINTENANCE

    private static long getNewTimeStamp() {
        return System.currentTimeMillis();
    }

    private static long getLocalTimeStamp(Context context, String statKeeperID) {
//        return 0;
        SharedPreferences updatePreferences = context.getSharedPreferences(statKeeperID + UPDATE_SETTINGS, Context.MODE_PRIVATE);
        return updatePreferences.getLong(LAST_UPDATE, 0);
    }

    public static void setLocalTimeStamp(long time, Context context, String statKeeperID) {
        SharedPreferences updatePreferences = context.getSharedPreferences(statKeeperID + UPDATE_SETTINGS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = updatePreferences.edit();
        editor.putLong(LAST_UPDATE, time);
        editor.apply();
    }

    private static long getCloudTimeStamp(DocumentSnapshot documentSnapshot, String statKeeperID) {
        Map<String, Object> data = documentSnapshot.getData();
        long cloudTimeStamp;
        Object object = data.get(LAST_UPDATE);
        if (object == null) {
            cloudTimeStamp = 0;
            updateCloudTimeStamp(cloudTimeStamp, statKeeperID);
        } else {
            cloudTimeStamp = (long) object;
        }
        return cloudTimeStamp;
    }

    public static void updateTimeStamps(final Context context, final String statKeeperID) {

        final long newTimeStamp = getNewTimeStamp();
        final long localTimeStamp = getLocalTimeStamp(context, statKeeperID);

        FirebaseFirestore.getInstance().collection(LEAGUE_COLLECTION).document(statKeeperID).get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            long cloudTimeStamp = getCloudTimeStamp(task.getResult(), statKeeperID);

                            if (localTimeStamp >= cloudTimeStamp) {
                                updateLocalTimeStamp(newTimeStamp, context, statKeeperID);
                            }
                            updateCloudTimeStamp(newTimeStamp, statKeeperID);
                        }
                    }
                });

    }

    private static void updateLocalTimeStamp(long timestamp, Context context, String statKeeperID) {
        SharedPreferences updatePreferences = context.getSharedPreferences(statKeeperID + UPDATE_SETTINGS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = updatePreferences.edit();
        editor.putLong(LAST_UPDATE, timestamp);
        editor.apply();
    }

    private static void updateCloudTimeStamp(long timestamp, String statKeeperID) {
        DocumentReference leagueDoc = FirebaseFirestore.getInstance().collection(LEAGUE_COLLECTION).document(statKeeperID);
        leagueDoc.update(LAST_UPDATE, timestamp);
    }


    //SETTING UPDATES

    public static void setUpdate(String firestoreID, int type, String statKeeperID, Context context) {

        long timeStamp = System.currentTimeMillis();
        String collection;

        if (type == 0) {
            collection = FirestoreHelper.TEAMS_COLLECTION;
        } else if (type == 1) {
            collection = FirestoreHelper.PLAYERS_COLLECTION;
        } else {
            return;
        }

        DocumentReference documentReference = FirebaseFirestore.getInstance().collection(LEAGUE_COLLECTION).document(statKeeperID)
                .collection(collection).document(firestoreID);
        documentReference.update(StatsContract.StatsEntry.UPDATE, timeStamp);
        updateTimeStamps(context, statKeeperID);
    }

}