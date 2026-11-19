package com.myhosh.booking;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
public class MenuDataSeeder implements CommandLineRunner {

    private final MenuItemRepository menuItemRepository;

    public MenuDataSeeder(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    private MenuItem item(int order, String cat, String name, String desc, String tags, String priceStr) {
        MenuItem m = new MenuItem();
        m.setSortOrder(order);
        m.setCategory(cat);
        m.setName(name);
        m.setDescription(desc);
        m.setDietaryTags(tags);
        m.setPrice(new BigDecimal(priceStr));
        m.setAvailable(true);
        m.setUpdatedAt(Instant.now());
        return m;
    }

    @Override
    public void run(String... args) {
        if (menuItemRepository.count() > 0) {
            return;
        }

        List<MenuItem> seed = List.of(
                // COLD MEZE
                item(1, "Cold Meze", "Cacik", "Cucumber dip with creamy yoghurt, garlic, mint and olive oil.", "GF", "5.45"),
                item(2, "Cold Meze", "Humus", "Chickpeas, blended with tahini paste, garlic, lemon and olive oil.", "VG, GF", "5.45"),
                item(3, "Cold Meze", "Kisir", "Freshly chopped parsley with bulgur (crushed wheat), tomato, cucumber, peppers, onion, mixed with pomegranate sauce and olive oil dressing.", "VG", "5.45"),
                item(4, "Cold Meze", "Stuffed Vine Leaves", "Vine leaves stuffed with flavoured rice, served with yoghurt.", "VG", "5.45"),
                item(5, "Cold Meze", "Patlican Sogurme", "Grilled aubergine pate, a hint of garlic mixed with creamy yoghurt and olive oil.", "GF", "5.45"),
                item(6, "Cold Meze", "Aubergine Napolitana (Saksuka)", "Pan fried aubergine marinated with virgin olive oil, fresh garlic, tomato, red and green peppers, onion, with traditional tomato sauce.", "VG, GF", "5.45"),
                item(7, "Cold Meze", "Tarator", "Combination of sweet carrots, spinach, yoghurt seasoned with garlic, olive oil and dill.", "V, GF", "5.45"),
                item(8, "Cold Meze", "Beetroot & Yogurt Dip", "Chop of raw beetroot seasoned with garlic, olive oil and yoghurt.", "V, GF", "5.45"),
                item(9, "Cold Meze", "Tarama", "Freshly prepared whipped cod roe fish paste.", "Fish", "5.45"),
                item(10, "Cold Meze", "Cold Meze Platter", "Humus, Cacik, Kisir, Stuffed Vine Leaves, Beetroot Yoghurt, Aubergine Napolitana.", "Platter", "16.95"),

                // HOT MEZE
                item(11, "Hot Meze", "Daily Soup", "Homemade soup served with homemade bread.", "Daily", "6.45"),
                item(12, "Hot Meze", "Borek", "Filo pastry delicately rolled and stuffed with feta cheese and parsley, served nice and crispy with sweet chillies.", "V, N", "6.45"),
                item(13, "Hot Meze", "Halloumi Cheese", "Grilled halloumi (Goat's Cheese from Cyprus).", "V, GF", "7.45"),
                item(14, "Hot Meze", "Falafel", "Deep fried combination of crushed chickpeas and broad beans tossed with fresh herbs, served on humus.", "VG", "7.45"),
                item(15, "Hot Meze", "Crispy Prawn", "Fried prawns battered in breadcrumbs, served with sweet chilli.", "Seafood", "9.45"),
                item(16, "Hot Meze", "Humus Kavurma", "Traditional humus warmed in the oven topped with fine and diced lamb.", "Lamb", "8.45"),
                item(17, "Hot Meze", "Sucuk Izgara", "Slightly spicy grilled Turkish sausage.", "Beef/Spicy", "8.45"),
                item(18, "Hot Meze", "Whitebait", "Pan-fried whitebait served with rocket, lemon and tartar sauce.", "Seafood", "7.45"),
                item(19, "Hot Meze", "Garlic Mushroom", "Mushroom sauteed in butter, garlic and mixed herbs.", "V, GF", "7.45"),
                item(20, "Hot Meze", "Calamari", "Deep fried squid served with tartar sauce and lemon.", "Seafood", "8.95"),
                item(21, "Hot Meze", "Tiger Prawns", "Tiger prawns sauteed in fresh garlic and herbs, served with creamy tomato sauce.", "Seafood", "9.95"),
                item(22, "Hot Meze", "Hot Meze Platter", "Borek, Calamari, Whitebait, Halloumi Cheese, Falafel, Sucuk.", "Platter", "17.95"),

                // SALAD
                item(23, "Salad", "Shepherds Salad (Coban Salata)", "Tomato, cucumber, green peppers, onion, parsley, sumac and olive oil dressing.", "VG", "6.95"),
                item(24, "Salad", "Feta Cheese Salad", "Tomato, cucumber, red onion, lettuce, feta cheese, sumac and olive oil dressing.", "V", "9.95"),
                item(25, "Salad", "Grilled Onion Salad", "Chunky cut of white onion seasoned and placed over the BBQ to grill then placed into a bed of turnip juice, sumac, parsley with pomegranate juice (10 mins).", "VG", "7.95"),
                item(26, "Salad", "Ezme Salad (Spicy)", "Tomato, green peppers, spicy pepper, parsley, onion, pomegranate sauce and olive oil dressing.", "V", "6.95"),

                // THE CHARCOAL GRILL
                item(27, "The Charcoal Grill", "Chicken Shish", "Lean chunks of chicken breast skewered and grilled over charcoal.", "Grill", "19.95"),
                item(28, "The Charcoal Grill", "Chicken Kofte", "Marinated minced chicken skewered with mix peppers, garlic, herbs and grilled over charcoal.", "Grill", "19.95"),
                item(29, "The Charcoal Grill", "Chicken Wings", "Marinated chicken wings grilled over charcoal.", "Grill", "18.95"),
                item(30, "The Charcoal Grill", "Lamb Shish", "Marinated lean tender cubes of lamb skewered and grilled over charcoal.", "Grill", "23.95"),
                item(31, "The Charcoal Grill", "Adana Kebab", "Lean minced lamb skewered and grilled over charcoal.", "Grill", "20.95"),
                item(32, "The Charcoal Grill", "Lamb Chops (Pirzola) (4pcs)", "Best end of lamb cutlets grilled over charcoal.", "Grill", "26.95"),
                item(33, "The Charcoal Grill", "Lamb Ribs (8 pcs)", "Succulent seasoned lamb ribs grilled over charcoal.", "Grill", "22.95"),
                item(34, "The Charcoal Grill", "Mix Shish", "A combination of 2: Chicken Shish, Lamb Shish OR Adana Kebab.", "Grill", "23.95"),
                item(35, "The Charcoal Grill", "Mixed Grill", "Lamb shish, chicken shish and adana kebab.", "Grill", "26.95"),
                item(36, "The Charcoal Grill", "Grill Sharing Platter", "For 3-4 People. Humus, Cacik, Tarama, Beetroot & Yogurt Dip, Lamb Shish, Chicken Shish, Adana Kofte, Chicken Kofte, 6pcs Lamb Ribs, 8pcs Chicken Wings. Served with salad, rice, bulgur and chips.", "Sharing", "79.95"),

                // AUTHENTIC CUISINE
                item(37, "Authentic Cuisine", "Lamb Roll Beyti", "Marinated minced lamb seasoned with garlic and grilled over charcoal, wrapped in lavash bread with cheese, yoghurt topped with tomato sauce then drizzled with butter.", "Lamb", "23.95"),
                item(38, "Authentic Cuisine", "Chicken Roll Beyti", "Marinated minced chicken seasoned with garlic and grilled over charcoal, wrapped in lavash bread with cheese, yoghurt topped with tomato sauce then drizzled with butter.", "Chicken", "22.95"),
                item(39, "Authentic Cuisine", "Ali Nazik", "Chargrilled lean and adana kebab served on a bed of aubergine puree with garlic yoghurt and drizzled with butter.", "Lamb", "23.95"),
                item(40, "Authentic Cuisine", "Adana Kebab with Yoghurt", "Charcoal grilled adana prepared with tomato sauce and yoghurt, on a bed of bread drizzled with butter on top.", "Lamb", "22.95"),
                item(41, "Authentic Cuisine", "Lamb Shish with Yoghurt", "Lamb shish prepared with tomato sauce and yoghurt, on a bed of bread drizzled with butter on top.", "Lamb", "24.95"),
                item(42, "Authentic Cuisine", "Chicken Shish with Yoghurt", "Chicken shish prepared with tomato sauce and yoghurt, on a bed of bread drizzled with butter on top.", "Chicken", "22.95"),
                item(43, "Authentic Cuisine", "Lamb Casseroles", "A traditional Turkish dish with lamb, onions, mix peppers, garlic, mushroom, tomatoes and a secret blend of spices.", "Lamb", "23.95"),
                item(44, "Authentic Cuisine", "Chicken Casseroles", "A traditional Turkish dish with chicken, onions, mix peppers, garlic, mushroom, tomatoes and a secret blend of spices.", "Chicken", "21.95"),

                // VEGETARIAN DISHES
                item(45, "Vegetarian Dishes", "Veggie Casserole", "A traditional Turkish dish with onions, mix peppers, aubergine, mushroom, garlic, tomatoes and a secret blend of spices.", "VG", "18.95"),
                item(46, "Vegetarian Dishes", "Falafel", "Deep fried chickpeas balls served with a dash of humus.", "VG", "19.95"),

                // SEA FOOD
                item(47, "Sea Food", "Salmon Fillet", "Fillet of salmon grill with crushed fresh herbs, served with chips.", "Seafood", "22.95"),
                item(48, "Sea Food", "Seabass Fillet", "Fillet of seabass with fresh herbs and lemon, served with chips.", "Seafood", "22.95"),
                item(49, "Sea Food", "Sea Bass", "Whole seabass grill with fresh herbs, lemon and olive oil, served with chips.", "Seafood", "22.95"),
                item(50, "Sea Food", "Giant Tiger Prawns (Slightly Spicy)", "Prawns sauteed in fresh garlic, served with rice and bulgur.", "Seafood", "23.95"),
                item(51, "Sea Food", "Prawns Casseroles", "Prawns sauteed with onions, mix peppers, garlic, mushroom, tomatoes and a secret blend of spices.", "Seafood", "23.95"),
                item(52, "Sea Food", "Calamari", "Homemade deep-fried squid with tartar sauce and chips.", "Seafood", "21.95"),

                // KIDS MENU
                item(53, "Kids Menu", "Chicken Nuggets", "All served with chips.", "Kids", "8.45"),
                item(54, "Kids Menu", "Burger", "All served with chips.", "Kids", "8.45"),
                item(55, "Kids Menu", "Adana Kofte", "All served with chips.", "Kids", "9.45"),
                item(56, "Kids Menu", "Chicken Shish", "All served with chips.", "Kids", "9.45"),
                item(57, "Kids Menu", "Chicken Wings", "All served with chips.", "Kids", "9.45"),
                item(58, "Kids Menu", "Lamb Shish", "All served with chips.", "Kids", "11.95"),

                // SIDE ORDERS
                item(59, "Side Orders", "Rice", "Side order of rice.", "V", "2.50"),
                item(60, "Side Orders", "Feta Cheese (Beyaz Peynir)", "Turkish feta cheese side.", "V", "3.95"),
                item(61, "Side Orders", "Yogurt", "Fresh yoghurt side.", "V", "1.50"),
                item(62, "Side Orders", "Fries/Chips", "Crispy fries/chips.", "V", "3.95"),
                item(63, "Side Orders", "Extra Bread", "Traditional bread portion.", "V, G, D", "1.50")
        );

        menuItemRepository.saveAll(seed);
    }
}
