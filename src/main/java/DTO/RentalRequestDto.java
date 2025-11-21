package DTO;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class RentalRequestDto {
    public String BookerName;
    public int NumOfPeople;
    public int NumOfBedrooms;
    public double DistanceToLake;
    public String ClosestCity;
    public double DistanceToCity;
    public int ReqNumOfDays;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    public Date BookingDate;
    public int ShiftDays;

    public RentalRequestDto(String BookerName, int NumOfPeople, int NumOfBedrooms, double DistanceToLake, String ClosestCity,
                    double DistanceToCity, int ReqNumOfDays, Date BookingDate, int ShiftDays){
        this.BookerName = BookerName;
        this.NumOfPeople = NumOfPeople;
        this.NumOfBedrooms = NumOfBedrooms;
        this.DistanceToLake = DistanceToLake;
        this.ClosestCity = ClosestCity;
        this.DistanceToCity = DistanceToCity;
        this.ReqNumOfDays = ReqNumOfDays;
        this.BookingDate = BookingDate;
        this.ShiftDays = ShiftDays;
    }

    public RentalRequestDto() {
        // Default constructor
    }

}

