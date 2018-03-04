package edu.ius.rwisman.gatetiming;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import static android.content.Context.LAYOUT_INFLATER_SERVICE;

public class ListViewAdapter extends BaseListViewAdapter {

    int startListViewChild;
    boolean newTimings = true;
    int newTimingsN = 0;

    public ListViewAdapter(GateTimingActivity activity, int gates, ListView listView, int N, double [] lows, double [] highs, long [] epochMilliseconds){
        super();
        this.activity=activity;
        this.list = new ArrayList<String[]>();
        this.timingDataList = new ArrayList<TimingData>();
        this.gates = gates;
        this.listView = listView;

        realPosition = 0;
        for(int i=0; i<N; i++) addItem(lows, highs, epochMilliseconds);                             // Create list from timings

        textViews = new TextView[6];

        averagesDisplayed = false;
        newTimings = true;

        inflater = (LayoutInflater) activity.getSystemService(LAYOUT_INFLATER_SERVICE);             // Get the background color
        View convertView = inflater.inflate(R.layout.timingslist, null);
        TextView tv = (TextView) convertView.findViewById(R.id.gate);
        if (tv.getBackground() instanceof ColorDrawable)
            backGroundColor = ((ColorDrawable) tv.getBackground()).getColor();
    }

    public void setBackGroundColor(int position, final int color) {

        final int finalPosition = position;
        startListViewChild = ((finalPosition / gates) * gates) - listView.getFirstVisiblePosition();// Valid when all gates in run are already on screen

        if (startListViewChild < 0)                                                                 // Not all gate rows are visible at top
            listView.setSelection((finalPosition / gates) * gates);
        else
            if(position/gates*gates+gates > listView.getLastVisiblePosition())                      // Not all gate rows are visible at bottom
                listView.setSelection(listView.getFirstVisiblePosition()+gates-1);

        listView.post(new Runnable() {                                                              // listView.post needed to wait turn after listView.setSelection()
            public void run() {
                startListViewChild = ((finalPosition / gates) * gates) - listView.getFirstVisiblePosition();    // Recalculate in case had to select additional rows at top or bottom
//                if(DEBUG.ON)
//                    System.out.println("ListViewAdapter setBackGroundColor startListViewChild:"+startListViewChild+" ");
                for(int j=startListViewChild; j<startListViewChild+gates; j++) {

                    ViewGroup row = (ViewGroup) listView.getChildAt(j);

                    textViews[0] = (TextView) row.findViewById(R.id.elapsed);
                    textViews[1] = (TextView) row.findViewById(R.id.gate);
                    textViews[2] = (TextView) row.findViewById(R.id.enter);
                    textViews[3] = (TextView) row.findViewById(R.id.timebetweenenter);
                    textViews[4] = (TextView) row.findViewById(R.id.exit);
                    textViews[5] = (TextView) row.findViewById(R.id.timebetweenexit);

                    for (int i = 0; i < textViews.length; i++)
                        textViews[i].setBackgroundColor(color);
                }
            }
        });
    }

    public void removeItemFromListDialog(final int position) {                                   // Remove selected run from timings
        final boolean averagesDisplayedLocal = averagesDisplayed;

        String [] startRun = (String []) getItem((position/gates)*gates);
        String [] endRun = (String []) getItem((position/gates)*gates+gates-1);

        AlertDialog.Builder alert = new AlertDialog.Builder(activity);

        alert.setCancelable(false);
        alert.setTitle(R.string.delete_label);
        alert.setMessage(activity.getResources().getString(R.string.deleterun) + " " + startRun[1]+" - "+endRun[1]);
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                int index = (position / gates) * gates;
                boolean adjustElapsedTime = Double.parseDouble(((String []) getItem(index))[0]) == 0.0;
                int count=0;

                for(int i=0; i<gates; i++) {
//                    if(DEBUG.ON)
//                        System.out.println("ListViewAdapter removeItemFromListDialog position:"+position+" getCount():"+getCount()+" realPosition:"+realPosition+" index:"+index+" timingDataList.size()"+timingDataList.size());

                    list.remove(index);                                                             // Remove same index as list items below shift up
                    if(index < timingDataList.size()) {                                             // Two lists not same length as empty items added to list - prevents deleting when last items selected
                        timingDataList.remove(index);
                        realPosition--;
                        count++;
                    }
                }

                activity.timingDataRemoved(count, index);                                           // Notify count and location of first timing of run where removed

                if(averagesDisplayedLocal)
                    removeAverages();                                                               // Sets averagesDisplayed to false, but need to hold onto

                for(int i=index; i<list.size(); i++) {
                    String [] columns = (String [])list.get(i);
                    int n = i % gates + 1;
                    int run = i / gates + 1;
                    columns[1]=String.format("%1d:%1d", run,n);
                }

                if(index == getCount()) index--;

                if(adjustElapsedTime && getCount() > 0) {                                           // Update elapsed time when 0.0 as this is the starting time for those that follow
                    double elapsedTime = 0.0;
                    double newStart = Double.parseDouble(((String []) getItem(index))[0]);
                    while( index < getCount() && Double.parseDouble(((String []) getItem(index))[0]) != 0.0) {
                        double currentElapsedTime = Double.parseDouble(((String []) getItem(index))[0]);
                        elapsedTime = currentElapsedTime - newStart;
                        ((String []) getItem(index))[0] = String.format("%.3f",elapsedTime);
                        index++;
                    }
                }

                if(averagesDisplayedLocal)
                    addAverages();
                notifyDataSetChanged();
            }
        });
        alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                        setBackGroundColor(position, backGroundColor);                              // Set background for all rows of selected run

                        listView.post(new Runnable() {                                              // listView.post needed to wait turn after setBackGroundColor
                            public void run() {
                                ViewGroup row = (ViewGroup) listView.getChildAt(((position / gates) * gates) - listView.getFirstVisiblePosition());

                                textViews[0] = (TextView) row.findViewById(R.id.elapsed);
                                textViews[1] = (TextView) row.findViewById(R.id.gate);
                                textViews[2] = (TextView) row.findViewById(R.id.enter);
                                textViews[3] = (TextView) row.findViewById(R.id.timebetweenenter);
                                textViews[4] = (TextView) row.findViewById(R.id.exit);
                                textViews[5] = (TextView) row.findViewById(R.id.timebetweenexit);

                                for (int i = 0; i < textViews.length; i++)
                                    textViews[i].setBackgroundColor(Color.DKGRAY);                  // First row of this timing
                            }
                        });
                    }
        });
        alert.show();
    }

    public void addAverages(double lows[], double highs[]) {
        final int ENTRY=0;
        final int ENTRYDIFF=1;
        final int EXIT=2;
        final int EXITDIFF=3;
        int n=0;
        int firstGate = 0;
        int runs = 0;

        if(getCount() < gates) return;                                                              // Don't take averages when no data

        if(averagesDisplayed)
            removeAverages();

        int N = list.size();

        double sums[][] = new double[4][gates];
        double averages[][] = new double[4][gates];

        for(int i=ENTRY; i<=EXITDIFF;i++)
            for(int j=0; j<gates; j++)
                sums[i][j]=0;

        runs = N / gates;

        for(int i=0; i<N; i++) {
            n = i % gates;                                                                        // n = number of gate
            firstGate = i - n;

            switch (n) {
                case 0 :
                    sums[ENTRY][n]=lows[i]-lows[i]+sums[ENTRY][n];                                  // Enter time
                    sums[EXIT][n]=highs[i]-lows[i]+sums[EXIT][n];                                   // Exit time
                    break;
                default :
                    sums[ENTRY][n]=lows[i]-lows[firstGate]+sums[ENTRY][n];                          // Enter time
                    sums[ENTRYDIFF][n]=lows[i]-lows[i-1]+sums[ENTRYDIFF][n];                        // Interval time
                    sums[EXIT][n]=highs[i]-lows[firstGate]+sums[EXIT][n];                           // Exit time
                    sums[EXITDIFF][n]=highs[i]-highs[i-1]+sums[EXITDIFF][n];                        // Interval time
            }
        }

        for(int j=0; j<gates; j++)
            for(int i=ENTRY; i<=EXITDIFF;i++)
                averages[i][j] = sums[i][j] / runs;

        for(int j=0; j<gates; j++){
            String columns[] = new String[6];
            columns[0]=" ";
            columns[1]=String.format("Mean:%d", (j+1));
            columns[2]=String.format("%.3f", averages[ENTRY][j]);                                  // Enter time
            columns[3]=String.format("%.3f", averages[ENTRYDIFF][j]);                              // Interval time
            columns[4]=String.format("%.3f", averages[EXIT][j]);                                   // Exit time
            columns[5]=String.format("%.3f", averages[EXITDIFF][j]);                               // Interval time

            list.add(columns);
        }
        averagesDisplayed = true;
    }

    public void removeAverages() {
        if(!averagesDisplayed)
            return;

        for(int i=0; i<gates; i++)
            deleteLastItem();                                                                       // Remove average for each gate

        averagesDisplayed = false;
    }

    public String getItemString(int position) {
        String [] columns = new String[6];
        columns = (String []) getItem(position);
        return columns[0]+columns[1]+columns[2]+columns[3]+columns[4]+columns[5];
    }

    public void addItem (double [] lows, double [] highs, long [] epochMilliseconds) {              // Add timing results to list for display

        String columns[]=new String[6];

        int N = realPosition;                                                                       //list.size(); // N = number of timings in list
        int n = N % gates + 1;                                                                      // n = number of gate to add
        int firstGate = N - n + 1;
        int run = N / gates + 1;
        double elapsedTime=0;

        if(N >= 1 && lows[N] < lows[N-1]) {
            newTimings = true;
            newTimingsN = N;
        }

        if(newTimings || N == 0) {
            elapsedTime = 0;
        }
        else {
            elapsedTime = lows[N]-lows[newTimingsN];
        }
        newTimings = false;

        columns[0]=String.format("%.3f",elapsedTime);
        columns[1]=String.format("%1d:%1d", run,n);

        switch (n) {
            case 1 :
                columns[2]=String.format("%.3f", lows[N]-lows[N]);                                  // Enter time
                columns[3]="";                                                                      // Interval time
                columns[4]=String.format("%.3f", highs[N]-lows[N]);                                 // Exit time
                columns[5]="";                                                                      // Interval time
                break;
            default :
                columns[2]=String.format("%.3f", lows[N]-lows[firstGate]);                          // Enter time
                columns[3]=String.format("%.3f", lows[N]-lows[N-1]);                                // Interval time
                columns[4]=String.format("%.3f", highs[N]-lows[firstGate]);                         // Exit time
                columns[5]=String.format("%.3f", highs[N]-highs[N-1]);                              // Interval time
        }
/*
        if(DEBUG.ON) {
            System.out.println("0 ListViewAdapter addItem list.size():"+list.size()+" realPosition:"+realPosition+" N:"+N+" lows["+N+"]="+lows[N]+(n!=1?(" lows["+(N-1)+"]="+lows[N-1]+" lows["+(firstGate)+"]="+lows[firstGate]):""));
            for(int i=0;i<list.size();i++) {
                String [] cols=list.get(i);
                System.out.print("list(" + i + ")=");
                for (int j = 0; j < columns.length; j++)
                    System.out.print(cols[j]+"|");
                System.out.println();
            }
        }
*/
        if(n == 1) {
            list.add(columns);

            for(int i=2; i<=gates;i++) {
                columns = new String [6];

                columns[0]="0.0";
                columns[1] = String.format("%1d:%1d", run, i);
                columns[2]="";
                columns[3]="";
                columns[4]="";
                columns[5]="";
                list.add(columns);
            }
        }
        else
            list.set(N,columns);

        realPosition++;
/*
            if(DEBUG.ON) {
                System.out.println("1 ListViewAdapter addItem list.size():"+list.size()+" realPosition:"+realPosition+" N:"+N+" lows["+N+"]="+lows[N]+(n!=1?(" lows["+(N-1)+"]="+lows[N-1]+" lows["+(firstGate)+"]="+lows[firstGate]):""));
                for(int i=0;i<list.size();i++) {
                    String [] cols=list.get(i);
                    System.out.print("list(" + i + ")=");
                    for (int j = 0; j < columns.length; j++)
                        System.out.print(cols[j]+"|");
                    System.out.println();
                }
            }
*/
        timingDataList.add(new TimingData(lows[N], highs[N], epochMilliseconds[N]));

        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
//        if(DEBUG.ON)
//            System.out.println("ListViewAdapter getView position:"+position+" getCount():"+getCount());

        ViewHolder holder = null;
        if(convertView == null) {                                                                    // Create new when == null, Recycle when != null
            convertView = inflater.inflate(R.layout.timingslist, null);
            holder = new ViewHolder();
            holder.textViews[0] = (TextView) convertView.findViewById(R.id.elapsed);
            holder.textViews[1] = (TextView) convertView.findViewById(R.id.gate);
            holder.textViews[2] = (TextView) convertView.findViewById(R.id.enter);
            holder.textViews[3] = (TextView) convertView.findViewById(R.id.timebetweenenter);
            holder.textViews[4] = (TextView) convertView.findViewById(R.id.exit);
            holder.textViews[5] = (TextView) convertView.findViewById(R.id.timebetweenexit);
            convertView.setTag(holder);
        }
        else
            holder = (ViewHolder)convertView.getTag();

        String columns[]=null;
        columns = (String [])list.get(position);

        for(int i=0; i < holder.textViews.length;i++) {
            if (position % gates == 0)
                holder.textViews[i].setBackgroundColor(Color.DKGRAY);                               // First row of this timing
            else
                holder.textViews[i].setBackgroundColor(backGroundColor);

            holder.textViews[i].setText(columns[i]);
        }
        return convertView;
    }
}

class ViewHolder {
    public TextView [] textViews = new TextView[6];
}
