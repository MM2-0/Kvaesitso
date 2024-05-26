package de.mm20.launcher2.search.location

enum class LocationCategory {
    /**
     * Restaurant
     */
    RESTAURANT,

    /**
     * Fast food
     */
    FAST_FOOD,

    /**
     * Bar
     */
    BAR,

    /**
     * Cafe
     */
    CAFE,

    /**
     * Hotel
     */
    HOTEL,

    /**
     * Supermarket
     */
    SUPERMARKET,

    /**
     * Place of interest
     */
    OTHER,

    /**
     * School
     */
    SCHOOL,

    /**
     * Parking
     */
    PARKING,

    /**
     * Gas station
     */
    FUEL,

    /**
     * Restroom
     */
    TOILETS,

    /**
     * Pharmacy
     */
    PHARMACY,

    /**
     * Hospital
     */
    HOSPITAL,

    /**
     * Post office
     */
    POST_OFFICE,

    /**
     * Pub
     */
    PUB,

    /**
     * Graveyard
     */
    GRAVE_YARD,

    /**
     * Doctors
     */
    DOCTORS,

    /**
     * Police
     */
    POLICE,

    /**
     * Dentist
     */
    DENTIST,

    /**
     * Library
     */
    LIBRARY,

    /**
     * College
     */
    COLLEGE,

    /**
     * Ice cream parlor
     */
    ICE_CREAM,

    /**
     * Theater
     */
    THEATRE,

    /**
     * Public building
     */
    PUBLIC_BUILDING,

    /**
     * Cinema
     */
    CINEMA,

    /**
     * Nightclub
     */
    NIGHTCLUB,

    /**
     * Biergarten
     */
    BIERGARTEN,

    /**
     * Clinic
     */
    CLINIC,

    /**
     * University
     */
    UNIVERSITY,

    /**
     * Department store
     */
    DEPARTMENT_STORE,

    /**
     * Apparel store
     */
    CLOTHES,

    /**
     * Convenience store
     */
    CONVENIENCE,

    /**
     * Hairdresser
     */
    HAIRDRESSER,

    /**
     * Car repair
     */
    CAR_REPAIR,

    /**
     * Beauty salon
     */
    BEAUTY,

    /**
     * Bookstore
     */
    BOOKS,

    /**
     * Bakery
     */
    BAKERY,

    /**
     * Car service
     */
    CAR,

    /**
     * Cell phone store
     */
    MOBILE_PHONE,

    /**
     * Furniture store
     */
    FURNITURE,

    /**
     * Liquor store
     */
    ALCOHOL,

    /**
     * Flower shop
     */
    FLORIST,

    /**
     * Hardware store
     */
    HARDWARE,

    /**
     * Electronics store
     */
    ELECTRONICS,

    /**
     * Shoe store
     */
    SHOES,

    /**
     * Mall
     */
    MALL,

    /**
     * Optometrist
     */
    OPTICIAN,

    /**
     * Jewelry store
     */
    JEWELRY,

    /**
     * Gift shop
     */
    GIFT,

    /**
     * Bicycle store
     */
    BICYCLE,

    /**
     * Laundry
     */
    LAUNDRY,

    /**
     * Computer store
     */
    COMPUTER,

    /**
     * Tobacco store
     */
    TOBACCO,

    /**
     * Wine store
     */
    WINE,

    /**
     * Photo shop
     */
    PHOTO,

    /**
     * Coffee shop
     */
    COFFEE_SHOP,

    /**
     * Bank
     */
    BANK,

    /**
     * Soccer
     */
    SOCCER,

    /**
     * Basketball
     */
    BASKETBALL,

    /**
     * Tennis
     */
    TENNIS,

    /**
     * Fitness
     */
    FITNESS,

    /**
     * Tram stop
     */
    TRAM_STOP,

    /**
     * Railway station
     */
    RAILWAY_STATION,

    /**
     * Railway stop
     */
    RAILWAY_STOP,

    /**
     * Bus station
     */
    BUS_STATION,

    /**
     * ATM
     */
    ATM,

    /**
     * Art store
     */
    ART,

    /**
     * Kiosk
     */
    KIOSK,

    /**
     * Bus stop
     */
    BUS_STOP,

    /**
     * Museum
     */
    MUSEUM,

    /**
     * Parcel locker
     */
    PARCEL_LOCKER,

    /**
     * Drug store
     */
    CHEMIST,

    /**
     * Travel agency
     */
    TRAVEL_AGENCY,

    /**
     * Fitness center
     */
    FITNESS_CENTRE;

    companion object {
        fun valueOfOrNull(string: String): LocationCategory? {
            return try {
                LocationCategory.valueOf(string)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}