package edu.heda.e4mqtt;

import com.google.gson.annotations.SerializedName;

public class EventData {

    @SerializedName("device")
    public String device;

    @SerializedName("type")
    public String type;

    @SerializedName("run")
    public String run;

    @SerializedName("value")
    public double value;
    @SerializedName("timestamp")
    public long timestamp;
    @SerializedName("x")
    public int x;
    @SerializedName("y")
    public int y;
    @SerializedName("z")
    public int z;


    @Override
    public String toString() {
        return "EventData{" +
                "device='" + device + '\'' +
                ", type='" + type + '\'' +
                ", run='" + run + '\'' +
                ", value=" + value +
                ", timestamp=" + timestamp +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }
}
