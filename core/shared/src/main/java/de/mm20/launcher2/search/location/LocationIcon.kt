package de.mm20.launcher2.search.location

enum class LocationIcon {
    Car,
    CarRental,
    CarRepair,
    CarWash,
    ChargingStation,
    GasStation,
    Parking,
    Bus,
    Tram,
    Train,
    Subway,
    CableCar,
    Airport,
    Boat,
    Taxi,
    Moped,
    Bike,
    Motorcycle,
    ElectricScooter,

    ArtGallery,
    Museum,
    Theater,
    MovieTheater,
    AmusementPark,
    NightClub,
    ConcertHall,
    Stadium,
    Casino,
    Circus,

    Hotel,

    Restaurant,
    Cafe,
    FastFood,
    Pizza,
    Burger,
    Kebab,
    IceCream,
    Ramen,
    Soup,
    Bar,
    Brunch,
    Breakfast,

    Shopping,
    Florist,
    Kiosk,
    FurnitureStore,
    CellPhoneStore,
    BookStore,
    ClothingStore,
    ConvenienceStore,
    DiscountStore,
    JewelryStore,
    LiquorStore,
    PetStore,
    ShoppingMall,
    Supermarket,
    Bakery,
    Optician,
    Pharmacy,
    HairSalon,
    Laundromat,

    Sports,
    FitnessCenter,
    Soccer,
    Basketball,
    Tennis,
    Golf,
    Baseball,
    AmericanFootball,
    Hiking,
    Swimming,
    Surfing,
    Motorsports,
    Handball,
    Volleyball,
    Skiing,
    Kayaking,
    Skateboarding,
    Cricket,
    MartialArts,
    NordicWalking,
    Paragliding,
    Gymnastics,
    Snowboarding,
    Hockey,
    Rugby,

    Bank,
    Atm,

    Physician,
    Dentist,
    Hospital,
    Clinic,

    Park,
    Forest,

    Monument,
    Church,
    Mosque,
    Synagogue,
    BuddhistTemple,
    HinduTemple,

    GovernmentBuilding,
    Police,
    FireDepartment,
    Courthouse,
    PostOffice,
    Library,
    School,
    University,
    PublicBathroom;

    companion object {
        fun valueOfOrNull(string: String): LocationIcon? {
            return try {
                valueOf(string)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

}