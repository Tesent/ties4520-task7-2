package DTO;

import java.net.URL;

public class RentalResponseDto {
    public String BookerName;
    public long BookingNumber;
    public String BookingAddress;
    public URL CottageImageUrl;
    public int CottageRealCapacity;
    public int NumOfBedrooms;
    public double DistanceToLake;
    public String ClosestCity;
    public double DistanceToClosestCity;
    //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    public String StartOfBooking;
    //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    public String EndOfBooking;

    public RentalResponseDto(String BookerName, long BookingNumber, String BookingAddress, URL CottageIamgeUrl, int CottageRealCapacity,
                             int NumOfBedrooms,double DistanceToLake, String ClosestCity, double DistanceToClosestCity,
                             String StartOfBooking, String EndOfBooking){
        this.BookerName = BookerName;
        this.BookingNumber = BookingNumber;
        this.BookingAddress = BookingAddress;
        this.CottageImageUrl = CottageIamgeUrl;
        this.CottageRealCapacity = CottageRealCapacity;
        this.NumOfBedrooms = NumOfBedrooms;
        this.DistanceToLake = DistanceToLake;
        this.ClosestCity = ClosestCity;
        this.DistanceToClosestCity = DistanceToClosestCity;
        this.StartOfBooking = StartOfBooking;
        this.EndOfBooking = EndOfBooking;

    }

    public RentalResponseDto() {
        // Default constructor
    }

}
