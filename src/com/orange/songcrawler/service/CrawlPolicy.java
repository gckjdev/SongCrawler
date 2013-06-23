package com.orange.songcrawler.service;

/**
 *  用于爬虫策略,如
 *  　　对于OneShot爬虫,由于百度的IP访问限制,每个实例进程只抓取部分字母范围　
 */
public class CrawlPolicy {

	
	public enum NameCapital {
		A("A"), B("B"), C("C"), D("D"), E("E"), F("F"), G("G"),
		H("H"), I("I"), J("J"), K("K"), L("L"), M("M"), N("N"),
		O("O"), P("P"), Q("Q"), R("R"), S("S"), T("T"),
		U("U"), V("V"), W("W"), X("X"), Y("Y"), Z("Z");
		
		private final String capital;
		NameCapital(String capital) {
			this.capital = capital;
		}
		
		public static NameCapital valueOf(int ord) {
			for (NameCapital nc: NameCapital.values()) {
				if (nc.ordinal() == ord)
					return nc;
			}
			return null;
		}
		
		public String getCapital() {
			return capital;
		}
	}

	public static NameCapital[] dispatchNameCapitalRange(String host) {
		
		// 抓singersRange[0]至singersRange[１]的所有歌曲
		NameCapital[] nameCapitalRange = new NameCapital[2];
		if (host != null) {
			switch (host) {
				case "1":
				/* A, B, C, D, E */
				nameCapitalRange[0] = NameCapital.A;
				nameCapitalRange[1] = NameCapital.E;
				break;
			case "2":
				/* F, G, H, I, J, K */
				/* I字母很少,　所以安排这机器负责６个字母 */
				nameCapitalRange[0] = NameCapital.F;
				nameCapitalRange[1] = NameCapital.K;
				break;
			case "3":
				/* L, M, N, O, P */
				nameCapitalRange[0] = NameCapital.L;
				nameCapitalRange[1] = NameCapital.P;
				break;
			case "4":
				/* Q, R, S, T, U */
				nameCapitalRange[0] = NameCapital.Q;
				nameCapitalRange[1] = NameCapital.U;
				break;
			case "5":
				/* V, W, X, Y, Z */
				nameCapitalRange[0] = NameCapital.V;
				nameCapitalRange[1] = NameCapital.Z;
				break;
			default:
				return null;
			}
		} else {
			/* Just for test */
			nameCapitalRange[0] = NameCapital.C;
			nameCapitalRange[1] = NameCapital.C;
		}
		    
		return nameCapitalRange;
	}
	

	
}
