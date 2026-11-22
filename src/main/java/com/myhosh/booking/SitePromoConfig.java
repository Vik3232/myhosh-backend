package com.myhosh.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "site_promo_config")
public class SitePromoConfig {

    @Id
    private Long id = 1L;

    // --- Hero Slide 1 ---
    private boolean slide1Enabled = true;

    @Column(length = 1000)
    private String slide1ImageUrl = "/images/meze-spread.webp";

    @Column(length = 250)
    private String slide1Headline = "Authentic Turkish Hospitality";

    @Column(length = 500)
    private String slide1Subheadline = "Charcoal grill, fresh meze, and a warm dining experience in the heart of Salisbury.";

    @Column(length = 100)
    private String slide1CtaText = "Reserve a Table";

    // --- Hero Slide 2 (Optional Promotion) ---
    private boolean slide2Enabled = false;

    @Column(length = 1000)
    private String slide2ImageUrl = "";

    @Column(length = 250)
    private String slide2Headline = "";

    @Column(length = 500)
    private String slide2Subheadline = "";

    @Column(length = 100)
    private String slide2CtaText = "Reserve a Table";

    // --- Hero Slide 3 (Optional Promotion) ---
    private boolean slide3Enabled = false;

    @Column(length = 1000)
    private String slide3ImageUrl = "";

    @Column(length = 250)
    private String slide3Headline = "";

    @Column(length = 500)
    private String slide3Subheadline = "";

    @Column(length = 100)
    private String slide3CtaText = "Reserve a Table";

    // --- Event Poster Popup ---
    private boolean posterEnabled = false;

    @Column(length = 1000)
    private String posterImageUrl = "";

    @Column(length = 250)
    private String posterTitle = "";

    @Column(length = 500)
    private String posterSubtitle = "";

    @Column(length = 100)
    private String posterCtaText = "Reserve for This Event";

    public SitePromoConfig() {}

    public static SitePromoConfig createDefault() {
        return new SitePromoConfig();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public boolean isSlide1Enabled() { return slide1Enabled; }
    public void setSlide1Enabled(boolean slide1Enabled) { this.slide1Enabled = slide1Enabled; }
    public String getSlide1ImageUrl() { return slide1ImageUrl; }
    public void setSlide1ImageUrl(String slide1ImageUrl) { this.slide1ImageUrl = slide1ImageUrl; }
    public String getSlide1Headline() { return slide1Headline; }
    public void setSlide1Headline(String slide1Headline) { this.slide1Headline = slide1Headline; }
    public String getSlide1Subheadline() { return slide1Subheadline; }
    public void setSlide1Subheadline(String slide1Subheadline) { this.slide1Subheadline = slide1Subheadline; }
    public String getSlide1CtaText() { return slide1CtaText; }
    public void setSlide1CtaText(String slide1CtaText) { this.slide1CtaText = slide1CtaText; }

    public boolean isSlide2Enabled() { return slide2Enabled; }
    public void setSlide2Enabled(boolean slide2Enabled) { this.slide2Enabled = slide2Enabled; }
    public String getSlide2ImageUrl() { return slide2ImageUrl; }
    public void setSlide2ImageUrl(String slide2ImageUrl) { this.slide2ImageUrl = slide2ImageUrl; }
    public String getSlide2Headline() { return slide2Headline; }
    public void setSlide2Headline(String slide2Headline) { this.slide2Headline = slide2Headline; }
    public String getSlide2Subheadline() { return slide2Subheadline; }
    public void setSlide2Subheadline(String slide2Subheadline) { this.slide2Subheadline = slide2Subheadline; }
    public String getSlide2CtaText() { return slide2CtaText; }
    public void setSlide2CtaText(String slide2CtaText) { this.slide2CtaText = slide2CtaText; }

    public boolean isSlide3Enabled() { return slide3Enabled; }
    public void setSlide3Enabled(boolean slide3Enabled) { this.slide3Enabled = slide3Enabled; }
    public String getSlide3ImageUrl() { return slide3ImageUrl; }
    public void setSlide3ImageUrl(String slide3ImageUrl) { this.slide3ImageUrl = slide3ImageUrl; }
    public String getSlide3Headline() { return slide3Headline; }
    public void setSlide3Headline(String slide3Headline) { this.slide3Headline = slide3Headline; }
    public String getSlide3Subheadline() { return slide3Subheadline; }
    public void setSlide3Subheadline(String slide3Subheadline) { this.slide3Subheadline = slide3Subheadline; }
    public String getSlide3CtaText() { return slide3CtaText; }
    public void setSlide3CtaText(String slide3CtaText) { this.slide3CtaText = slide3CtaText; }

    public boolean isPosterEnabled() { return posterEnabled; }
    public void setPosterEnabled(boolean posterEnabled) { this.posterEnabled = posterEnabled; }
    public String getPosterImageUrl() { return posterImageUrl; }
    public void setPosterImageUrl(String posterImageUrl) { this.posterImageUrl = posterImageUrl; }
    public String getPosterTitle() { return posterTitle; }
    public void setPosterTitle(String posterTitle) { this.posterTitle = posterTitle; }
    public String getPosterSubtitle() { return posterSubtitle; }
    public void setPosterSubtitle(String posterSubtitle) { this.posterSubtitle = posterSubtitle; }
    public String getPosterCtaText() { return posterCtaText; }
    public void setPosterCtaText(String posterCtaText) { this.posterCtaText = posterCtaText; }
}