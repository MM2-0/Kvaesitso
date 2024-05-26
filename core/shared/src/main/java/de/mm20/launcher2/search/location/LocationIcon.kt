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

    ArtGallery,
    Museum,
    Theater,
    MovieTheater,
    AmusementPark,
    NightClub,
    Music,
    Stadium,
    Casino,

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
    Optometrist,
    Pharmacy,

    Physician,
    Dentist,
    Hospital,
    Clinic,

    Park,
    Forest,

    Church,
    Mosque,
    Synagogue,
    BuddhistTemple,
    HinduTemple,

    Police,
    FireDepartment,
    Courthouse,
    PostOffice,
    Bank,
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