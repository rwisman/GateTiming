package edu.ius.rwisman.gatetiming;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

public abstract class BaseListViewAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

    public TextView [] textViews;
    public GateTimingActivity activity;
    public ArrayList list;
    public ArrayList <TimingData> timingDataList;
    public ListView listView = null;

    public int gates=0;
    public boolean averagesDisplayed = false;
    public int backGroundColor = 0x202020;                                                          // Default background color if none defined
    public LayoutInflater inflater;
    public boolean displayElapsedTime = false;
    public int realPosition=0;

    public abstract void setBackGroundColor(final int position, final int color);
    public abstract void removeItemFromListDialog(final int position);
    public abstract void addAverages(double lows[], double highs[]);
    public abstract void removeAverages();
    public abstract void addItem (double [] lows, double [] highs, long [] epochMilliseconds) ;
    public abstract String getItemString(int position);


    public BaseListViewAdapter() { super(); }

    public void sortRun(View view){};
    public void sortTime(View view){};

    @Override
    public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) {
//        if(DEBUG.ON)
//            System.out.println("ListViewAdapter onItemClick position:"+position+" getCount():"+getCount()+" gates:"+gates);

        if(position == getCount()-1 && averagesDisplayed)                                           // Means selected - ignore
            return ;

        setBackGroundColor(position, Color.RED);

        removeItemFromListDialog(position);

        return ;
    }

    public void setDisplayElapsedTime(boolean displayElapsedTime) { this.displayElapsedTime = displayElapsedTime; }

    public boolean getDisplayElapsedTime() {
        return this.displayElapsedTime;
    }

    public ArrayList<TimingData> getTimingData() {
        return timingDataList;
    }

    public void setGates(int gates) {
        this.gates=gates;
    }

    public boolean getAveragesDisplayed() {
        return averagesDisplayed;
    }

    public void clear() {
        this.list = new ArrayList();
        this.timingDataList = new ArrayList<TimingData>();
        averagesDisplayed = false;
        realPosition = 0;
    }

    public void deleteLastItem() {
        if(getCount() >= 1)
            list.remove(getCount()-1);
    }

    public void addAverages() {
        double [] lows = new double[timingDataList.size()];
        double [] highs = new double[timingDataList.size()];

        for(int i=0;i<timingDataList.size(); i++) {
            lows[i] = timingDataList.get(i).low;
            highs[i] = timingDataList.get(i).high;
        }
        addAverages(lows, highs);
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    public Object getLastItem() {
        return getItem(getCount()-1);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}
