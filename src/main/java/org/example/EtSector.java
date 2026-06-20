package org.example;

public class EtSector {

    private String sectorName;
    private String sectorLink;
    private String change1D;
    private String change1W;
    private String change1M;
    private String change3M;
    private String change6M;
    private String change1Y;
    private String marketCapCr;
    private String numberOfStocks;

    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }

    public String getSectorLink() { return sectorLink; }
    public void setSectorLink(String sectorLink) { this.sectorLink = sectorLink; }

    public String getChange1D() { return change1D; }
    public void setChange1D(String change1D) { this.change1D = change1D; }

    public String getChange1W() { return change1W; }
    public void setChange1W(String change1W) { this.change1W = change1W; }

    public String getChange1M() { return change1M; }
    public void setChange1M(String change1M) { this.change1M = change1M; }

    public String getChange3M() { return change3M; }
    public void setChange3M(String change3M) { this.change3M = change3M; }

    public String getChange6M() { return change6M; }
    public void setChange6M(String change6M) { this.change6M = change6M; }

    public String getChange1Y() { return change1Y; }
    public void setChange1Y(String change1Y) { this.change1Y = change1Y; }

    public String getMarketCapCr() { return marketCapCr; }
    public void setMarketCapCr(String marketCapCr) { this.marketCapCr = marketCapCr; }

    public String getNumberOfStocks() { return numberOfStocks; }
    public void setNumberOfStocks(String numberOfStocks) { this.numberOfStocks = numberOfStocks; }

    @Override
    public String toString() {
        return "EtSector{"
                + "sectorName=" + sectorName
                + ", sectorLink=" + sectorLink
                + ", change1D=" + change1D
                + ", change1W=" + change1W
                + ", change1M=" + change1M
                + ", change3M=" + change3M
                + ", change6M=" + change6M
                + ", change1Y=" + change1Y
                + ", marketCapCr=" + marketCapCr
                + ", numberOfStocks=" + numberOfStocks
                + '}';
    }
}
