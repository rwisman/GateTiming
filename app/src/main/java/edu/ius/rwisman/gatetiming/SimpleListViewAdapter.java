package edu.ius.rwisman.gatetiming;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.threeten.bp.Instant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

public class SimpleListViewAdapter extends BaseListViewAdapter {

    final int MEAN = -1;

    static boolean sortRun = false;
    static boolean sortRunAscending = true;
    static boolean sortTime = false;
    static boolean sortTimeAscending = true;

    public SimpleListViewAdapter(GateTimingActivity activity, int gates, ListView listView, int N, double [] lows, double [] highs, long [] epochMilliseconds){
        super();
 //       if(DEBUG.ON)
 //           System.out.println("SimpleListViewAdapter ");

        this.activity=activity;
        this.list = new ArrayList<ListEntry>();
        this.timingDataList = new ArrayList<TimingData>();
        this.gates = gates;
        this.listView = listView;

        realPosition = 0;
        for(int i=0; i<N; i++) addItem(lows, highs, epochMilliseconds);                                                // Create list from timings

        textViews = new TextView[3];

        averagesDisplayed = false;

        inflater = (LayoutInflater) activity.getSystemService(LAYOUT_INFLATER_SERVICE);             // Get the background color
        View convertView = inflater.inflate(R.layout.simpletimingslist, null);
        TextView tv = (TextView) convertView.findViewById(R.id.run);
        if (tv.getBackground() instanceof ColorDrawable)
            backGroundColor = ((ColorDrawable) tv.getBackground()).getColor();
    }

    private void sort() {

        final boolean localAveragesDisplayed = averagesDisplayed;

        new Thread(new Runnable() {                                                                 // Wait for detection thread to finish before starting calibration
            public void run() {
                if(localAveragesDisplayed)
                    removeAverages();

                if (sortRun && sortRunAscending)
                    Collections.sort(list, new SortRunAscending());
                else if (sortRun && !sortRunAscending)
                    Collections.sort(list, new SortRunDescending());
                else if (sortTime && sortTimeAscending)
                    Collections.sort(list, new SortTimeAscending());
                else if (sortTime && !sortTimeAscending)
                    Collections.sort(list, new SortTimeDescending());

                if(localAveragesDisplayed)
                    addAverages();
            }
        }).start();
    }

    public void sortRun(View view) {
        sortRun = true;
        sortTime = false;
        sortRunAscending = !sortRunAscending;
        sort();
        notifyDataSetChanged();
    }

    public void sortTime(View view) {
        sortRun = false;
        sortTime = true;
        sortTimeAscending = !sortTimeAscending;
        sort();
        notifyDataSetChanged();
    }

    public void setBackGroundColor(final int position, final int color) {
        final int startListViewChild = position - listView.getFirstVisiblePosition();               // Valid when all gates in run are already on screen

        listView.post(new Runnable() {                                                              // listView.post needed to wait turn after listView.setSelection()
            public void run() {
                //             if(DEBUG.ON)
                //                    System.out.println("ListViewAdapter setBackGroundColor startListViewChild:"+startListViewChild+" ");
                ViewGroup row = (ViewGroup) listView.getChildAt(startListViewChild);

                textViews[0] = (TextView) row.findViewById(R.id.datetime);
                textViews[1] = (TextView) row.findViewById(R.id.run);
                textViews[2] = (TextView) row.findViewById(R.id.time);

                for (int i = 0; i < textViews.length; i++)
                    textViews[i].setBackgroundColor(color);
            }
        });
    }

    public void removeItemFromListDialog(final int position) {                                      // Remove selected run from timings

        final boolean averagesDisplayedLocal = averagesDisplayed;
        int bkgColor=backGroundColor;
        final int run = ((ListEntry)list.get(position)).run-1;
        final int lastGate = ((ListEntry)list.get(position)).gate;
        final int index = gates * run;

        ViewGroup row = (ViewGroup) listView.getChildAt(position - listView.getFirstVisiblePosition());

        TextView tv = (TextView) row.findViewById(R.id.run);
        if (tv.getBackground() instanceof ColorDrawable)
            bkgColor = ((ColorDrawable) tv.getBackground()).getColor();

        final int originalBackGroundColor = bkgColor;

        AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setCancelable(false);
        alert.setTitle(R.string.delete_label);
        alert.setMessage(activity.getResources().getString(R.string.delete) + " " + (run+1));
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                int count = 0;

                dialog.dismiss();

//                if(DEBUG.ON)
//                    System.out.println("ListViewAdapter removeItemFromListDialog index:"+index+" run:"+run);

                if (averagesDisplayedLocal)
                    removeAverages();

                Collections.sort(list, new SortRunAscending());                                     // Sort list to 'natural' organization


                list.remove(run);                                                                   // Remove same index as list items below shift up
                for(int i=0; i<lastGate; i++) {
                    if(index < timingDataList.size()) {                                             // Two lists not same length as empty items added to list - prevents deleting when last items selected

//                        if(DEBUG.ON)
//                            System.out.println("0 SimpleListViewAdapter removeItemFromListDialog position:"+position+" getCount():"+getCount()+" i:"+i+
//                                    " timingDataList("+index+")=("+timingDataList.get(index).low+"|"+timingDataList.get(index).high+")");
                        timingDataList.remove(index);

                        realPosition--;
                        count++;
                    }
                }

                for(int i=run; i<getCount(); i++) {

                    ListEntry e = (ListEntry)list.get(i);
                    e.run = i+1;
                    list.set(i,e);
//                    if(DEBUG.ON)
//                        System.out.println("1 SimpleListViewAdapter removeItemFromListDialog list.set("+i+","+columns[0]+"|"+columns[1]+")");
                }

                activity.timingDataRemoved(count, index);                                           // Notify count and location of first timing of run where removed

                if(averagesDisplayedLocal && getCount() != 0) {                                    // Hack, adding averages because sort() will remove.
                    addAverages();
                    sort();                                                                         // Sort back to original ordering
                }
                notifyDataSetChanged();
            }
        });
        alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                setBackGroundColor(position, originalBackGroundColor);                               // Set background for all rows of selected run
            }
        });
        alert.show();
    }

    public void addAverages(double lows[], double highs[]) {

        int n=0;
        int firstGate = 0;
        int runs = 0;
        int N;
        double sum = 0;
        double average = 0;

        if(DEBUG.ON) System.out.println("0 SimpleListViewAdapter addAverages() getCount:"+getCount()+" timingDataList.size():"+timingDataList.size()+" averageDisplayed:"+averagesDisplayed);

        if(averagesDisplayed)
            removeAverages();

        if(timingDataList.size() < 1) {
            notifyDataSetChanged();
            return;                                                                                 // Don't take averages when no data
        }

        N = timingDataList.size();

        runs = N/gates;

//        if(DEBUG.ON) System.out.println("1 SimpleListViewAdapter addAverages() N:"+N);

        for(int i=0; i<N; i++) {
            n = i % gates;                                                                          // n = number of gate
            firstGate = i - n;

            if(n == gates-1) {
                sum = lows[i] - lows[firstGate] + sum;
//                if(DEBUG.ON) System.out.println("2 SimpleListViewAdapter addAverages() time["+i+"]="+(lows[i] - lows[firstGate]));
            }
        }

        average = sum / runs;

//        if(DEBUG.ON) System.out.println("SimpleListViewAdapter addAverages() sum:"+sum + " average:"+average + " runs:"+runs);

        list.add(new ListEntry(0, MEAN, average, 0));                         // Flag MEAN to be displayed

        averagesDisplayed = true;
    }

    public void removeAverages() {
//        if(DEBUG.ON) System.out.println("0 SimpleListViewAdapter removeAverages() getCount:"+getCount()+" timingDataList.size():"+timingDataList.size()+" averageDisplayed:"+averagesDisplayed);

        if(!averagesDisplayed)
            return;

        deleteLastItem();                                                                           // Remove average for each gate

//        if(DEBUG.ON) System.out.println("1 SimpleListViewAdapter removeAverages() getCount:"+getCount()+" timingDataList.size():"+timingDataList.size()+" averageDisplayed:"+averagesDisplayed);

        averagesDisplayed = false;
    }

    public String getItemString(int position) {
        String [] columns = new String[3];
        columns = (String []) list.get(position);
        return columns[0]+columns[1]+columns[2];
    }

    @Override
    public Object getItem(int position) {
        String [] columns = new String[3];
        ListEntry e = (ListEntry)list.get(position);

        if(e.run == MEAN) {                                                                         // Flag for display of MEAN
            columns[0] = " ";
            columns[1] = activity.getString(R.string.mean);
            columns[2] = String.format("%.3f", e.time);
        }
        else {
//            String[] datetime = Instant.ofEpochMilli(e.epochMilliseconds).toString().split("T");
//            String[] time = datetime[1].split("Z");                                          // Throw away Z
//            columns[0] = datetime[0]+"T"+time[0];
            columns[0] = Instant.ofEpochMilli(e.epochMilliseconds).toString();
            columns[1] = String.valueOf(e.run);
            columns[2] = String.format("%.3f", e.time);
        }
        return columns;
    }

    public void addItem (double [] lows, double [] highs, long [] epochMilliseconds) {              // Add timing results to list for display
        int N = timingDataList.size();                                                              // N = current number of timings in list
        int n = N % gates+1;                                                                        // n = number of gate to add
        int firstGate = N - n + 1;
        int run = N / gates + 1;
//        if(DEBUG.ON) System.out.println("0 SimpleListViewAdapter addItem getCount():"+getCount()+" N:"+N+" run:"+run+" n:"+n+" gates:"+gates+" firstGate:"+firstGate);

        if(n == 1)
            list.add(new ListEntry(n, run, lows[N]-lows[firstGate], epochMilliseconds[N]));
        else
            list.set(run - 1, new ListEntry(n, run, lows[N]-lows[firstGate], epochMilliseconds[N]));

        notifyDataSetChanged();

        realPosition++;

        timingDataList.add(new TimingData(lows[N], highs[N], epochMilliseconds[N]));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
//        if(DEBUG.ON)
//            System.out.println("SimpleListViewAdapter getView position:"+position+" getCount():"+getCount());

        SimpleViewHolder holder = null;
        if(convertView == null) {                                                                   // Create new when == null, Recycle when != null
            convertView = inflater.inflate(R.layout.simpletimingslist, null);
            holder = new SimpleViewHolder();

            holder.textViews[0] = (TextView) convertView.findViewById(R.id.datetime);
            holder.textViews[1] = (TextView) convertView.findViewById(R.id.run);
            holder.textViews[2] = (TextView) convertView.findViewById(R.id.time);
            convertView.setTag(holder);
        }
        else
            holder = (SimpleViewHolder)convertView.getTag();

        String columns[] = new String[holder.textViews.length];
        ListEntry e = (ListEntry)list.get(position);

        if(e.run == MEAN) {                                                                         // Flag for display of MEAN
            columns[0] = null;
            columns[1] = activity.getString(R.string.mean);
            columns[2] = String.format("%.3f", e.time);
        }
        else {
            String[] datetime = Instant.ofEpochMilli(e.epochMilliseconds).toString().split("T");
            String[] time = datetime[1].split("Z");                                          // Throw away T and Z
            columns[0] = datetime[0]+"\n"+time[0];

            if (e.gate == gates)
                columns[1] = String.valueOf(e.run)+"\n";
            else
                columns[1] = String.valueOf(e.run) + ":" + String.valueOf(e.gate)+"\n";

            columns[2] = String.format("%.3f\n", e.time);
        }

        for(int i=0; i < holder.textViews.length;i++) {
            if (position % 2 == 0)                                                                  // Alternate background color
                holder.textViews[i].setBackgroundColor(Color.DKGRAY);
            else
                holder.textViews[i].setBackgroundColor(backGroundColor);

            holder.textViews[i].setText(columns[i]);
        }
        return convertView;
    }
}

class SimpleViewHolder {
    public TextView [] textViews = new TextView[3];
}

class ListEntry {
    public long epochMilliseconds;
    public int run;
    public double time;
    public int gate;

    public ListEntry(int gate, int run, double time, long epochMilliseconds) {
        this.epochMilliseconds = epochMilliseconds;
        this.gate = gate;
        this.run = run;
        this.time = time;
    }
}

class SortRunAscending implements Comparator<ListEntry> {
    public int compare(ListEntry a, ListEntry b)
    {
        return a.run - b.run;
    }
}

class SortRunDescending implements Comparator<ListEntry> {
    public int compare(ListEntry a, ListEntry b)
    {
        return b.run - a.run;
    }
}

class SortTimeAscending implements Comparator<ListEntry> {
    public int compare(ListEntry a, ListEntry b)
    {
        return a.time - b.time < 0 ? -1 : 1;
    }
}

class SortTimeDescending implements Comparator<ListEntry> {
    public int compare(ListEntry a, ListEntry b)
    {
        return b.time - a.time < 0 ? -1 : 1;
    }
}