# TIES4520 Task 7-2

## Example request to the remote backend service

POST http://localhost:8080/SSWAP_Remote_Service_2019-1.0-SNAPSHOT/mediator

BODY:

@prefix c: <http://example.com/cottage#> .
@prefix service: <http://example.com/CottageBookingService#> .
@prefix sswap: <http://sswapmeet.sswap.info/sswap#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

service:RequestQueryCottage
    rdf:type sswap:RequestGraph ;
    sswap:hasMapping [
        rdf:type sswap:Subject , c:BookingRequest ;
        c:distanceToLake 2000.0 ;
        c:distanceToClosestCity 0 ;
        c:numOfPeople 0 ;
        c:numOfBedrooms 0 ;
        c:closestCity "" ;
        c:reqNumOfDays 0 ;
        c:bookingDate "2015-11-04"^^xsd:date ;
    ] .
