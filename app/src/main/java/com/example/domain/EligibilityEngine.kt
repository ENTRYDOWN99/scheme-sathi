package com.example.domain

import com.example.data.model.CitizenProfile
import com.example.data.model.ConditionResult
import com.example.data.model.DocumentRequirement
import com.example.data.model.EligibilityEvaluationResult
import com.example.data.model.EligibilityOutcome
import com.example.data.model.GovernmentScheme
import com.example.data.model.LogicalCombinator
import com.example.data.model.RuleCondition
import com.example.data.model.RuleGroup
import com.example.data.model.RuleOperator

/**
 * Pure, synchronous, I/O-free deterministic eligibility engine for Scheme Saathi.
 *
 * Rules enforced:
 * - Never allows the LLM to decide eligibility.
 * - Missing information ("unknown" or -1) always yields UNKNOWN, never NOT_ELIGIBLE.
 * - Unconfirmed voice age (ageConfirmedViaVoice == false) yields UNKNOWN for age checks.
 * - External lookups yield UNKNOWN with explicit citizen verification guidance.
 * - ALL combinator: Any NOT_ELIGIBLE -> NOT_ELIGIBLE; else any UNKNOWN -> UNKNOWN; else ELIGIBLE.
 * - ANY combinator: Any ELIGIBLE -> ELIGIBLE; all NOT_ELIGIBLE -> NOT_ELIGIBLE; else UNKNOWN.
 */
object EligibilityEngine {

    fun currentReferenceYear(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    val initialFiveSchemes: List<GovernmentScheme> = listOf(
        // 1. PM-KISAN
        GovernmentScheme(
            id = "PM-KISAN",
            code = "PM-KISAN",
            nameEn = "PM-KISAN (Pradhan Mantri Kisan Samman Nidhi)",
            nameHi = "पीएम-किसान (प्रधानमंत्री किसान सम्मान निधि)",
            category = "Agriculture / कृषि",
            benefitSummaryEn = "Illustrative: ₹6,000 per year in 3 installments via Direct Benefit Transfer (subject to official PM-KISAN portal rules).",
            benefitSummaryHi = "उदाहरणार्थ: प्रति वर्ष ₹6,000 तीन समान किस्तों में सीधे बैंक खाते (DBT) में (आधिकारिक पोर्टल नियमों के अधीन)।",
            descriptionEn = "Income support scheme for landholding farmer families across India to meet agricultural input and domestic needs.",
            descriptionHi = "देश भर के भूमिधारक किसान परिवारों को कृषि और घरेलू आवश्यकताओं के लिए वित्तीय सहायता प्रदान करने वाली योजना।",
            officialSourceTitle = "PM-KISAN Official Portal (Ministry of Agriculture)",
            officialSourceUrl = "https://pmkisan.gov.in",
            verificationDate = "2026-01-15 (Illustrative Rule Set - Verify on Portal)",
            isThresholdVerifiedOfficial = false,
            isRuleStaleWarning = false,
            ruleVersion = "v1.2.0-illustrative",
            rootRuleGroup = RuleGroup(
                combinator = LogicalCombinator.ALL,
                conditions = listOf(
                    RuleCondition(
                        id = "pmkisan_age",
                        field = "age",
                        operator = RuleOperator.GTE,
                        targetValue = 18,
                        descriptionEn = "Applicant must be at least 18 years old",
                        descriptionHi = "आवेदक की आयु कम से कम 18 वर्ष होनी चाहिए"
                    ),
                    RuleCondition(
                        id = "pmkisan_land",
                        field = "ownsAgriculturalLand",
                        operator = RuleOperator.EQ,
                        targetValue = "yes",
                        descriptionEn = "Must own cultivable agricultural land in land records",
                        descriptionHi = "कृषि योग्य भूमि का स्वामित्व होना आवश्यक है"
                    ),
                    RuleCondition(
                        id = "pmkisan_tax",
                        field = "isIncomeTaxPayer",
                        operator = RuleOperator.EQ,
                        targetValue = "no",
                        descriptionEn = "Must not be an income tax payer in the last assessment year",
                        descriptionHi = "आयकर दाता नहीं होना चाहिए"
                    ),
                    RuleCondition(
                        id = "pmkisan_bank",
                        field = "hasBankAccount",
                        operator = RuleOperator.EQ,
                        targetValue = "yes",
                        descriptionEn = "Active DBT-enabled bank account required",
                        descriptionHi = "सक्रिय बैंक खाता होना आवश्यक है"
                    )
                )
            ),
            requiredDocuments = listOf(
                DocumentRequirement(
                    id = "doc_land_record",
                    nameEn = "Land Ownership Record (Khatauni / Khasra / RoR)",
                    nameHi = "भूमि स्वामित्व प्रमाण (खतौनी / खसरा / अधिकार अभिलेख)",
                    mandatory = true,
                    alternativesEn = "Patta Document or Mutation Certificate issued by Tehsildar",
                    alternativesHi = "तहसीलदार द्वारा जारी पट्टा या दाखिल-खारिज प्रमाण पत्र",
                    howToObtainEn = "Download from your State Bhulekh portal or visit the nearest Tehsil / CSC center.",
                    howToObtainHi = "अपने राज्य के भूलेख पोर्टल से डाउनलोड करें या नजदीकी तहसील / जन सेवा केंद्र (CSC) जाएं।"
                ),
                DocumentRequirement(
                    id = "doc_bank_passbook",
                    nameEn = "Bank Account Passbook with IFSC & NPCI DBT Seeding",
                    nameHi = "बैंक पासबुक (IFSC और DBT लिंकिंग सहित)",
                    mandatory = true,
                    alternativesEn = "Cancelled Cheque with printed account details or India Post Payments Bank statement",
                    alternativesHi = "खाता विवरण वाला कैंसिल चेक या इंडिया पोस्ट पेमेंट्स बैंक विवरण",
                    howToObtainEn = "Visit your bank branch or India Post Payments Bank counter.",
                    howToObtainHi = "अपनी बैंक शाखा या डाकघर भुगतान बैंक केंद्र से प्राप्त करें।"
                ),
                DocumentRequirement(
                    id = "doc_identity_card",
                    nameEn = "Government Voter ID / Kisan Credit Card / Ration Card (No Aadhaar stored in app)",
                    nameHi = "मतदाता पहचान पत्र / किसान क्रेडिट कार्ड / राशन कार्ड (ऐप में आधार संग्रहीत नहीं किया जाता)",
                    mandatory = true,
                    alternativesEn = "PAN Card, Driving License, or MGNREGA Job Card",
                    alternativesHi = "पैन कार्ड, ड्राइविंग लाइसेंस या मनरेगा जॉब कार्ड",
                    howToObtainEn = "Obtain from Election Commission portal, Tehsil, or CSC.",
                    howToObtainHi = "निर्वाचन आयोग पोर्टल या नजदीकी CSC से प्राप्त करें।"
                )
            ),
            applicationInstructionsEn = "1. Visit pmkisan.gov.in -> New Farmer Registration.\n2. Keep your Khatauni land record and DBT-linked bank passbook ready.\n3. Or carry this Guided Handoff Pack to your nearest Common Service Centre (CSC).",
            applicationInstructionsHi = "1. pmkisan.gov.in पर जाएं -> 'New Farmer Registration' चुनें।\n2. अपनी खतौनी और बैंक पासबुक तैयार रखें।\n3. या इस हैंडऑफ सारांश को नजदीकी जन सेवा केंद्र (CSC) ले जाएं।",
            cscOfficeGuidanceEn = "Locate your Gram Panchayat Bhawan, Patwari/Lekhpal office, or authorized CSC operator with your land Khatauni.",
            cscOfficeGuidanceHi = "अपनी खतौनी के साथ ग्राम पंचायत भवन, लेखपाल/पटवारी कार्यालय या अधिकृत जन सेवा केंद्र (CSC) से संपर्क करें।"
        ),

        // 2. NSAP-IGNOAPS
        GovernmentScheme(
            id = "NSAP-IGNOAPS",
            code = "NSAP-IGNOAPS",
            nameEn = "NSAP - Indira Gandhi National Old Age Pension Scheme",
            nameHi = "इंदिरा गांधी राष्ट्रीय वृद्धावस्था पेंशन योजना (NSAP-IGNOAPS)",
            category = "Senior Citizen Pension / वृद्धावस्था पेंशन",
            benefitSummaryEn = "Illustrative: Central share ₹200/month (age 60–79) and ₹500/month (age 80+) plus applicable State top-up.",
            benefitSummaryHi = "उदाहरणार्थ: 60–79 वर्ष के लिए केंद्रीय अंश ₹200/माह और 80+ वर्ष के लिए ₹500/माह + राज्य सरकार का अतिरिक्त अंशदान।",
            descriptionEn = "Monthly non-contributory pension for senior citizens aged 60 years or above belonging to Below Poverty Line (BPL) households.",
            descriptionHi = "गरीबी रेखा से नीचे (BPL) जीवन यापन करने वाले 60 वर्ष या उससे अधिक आयु के वरिष्ठ नागरिकों के लिए मासिक पेंशन।",
            officialSourceTitle = "National Social Assistance Programme (NSAP) Portal",
            officialSourceUrl = "https://nsap.nic.in",
            verificationDate = "2025-11-20 (Illustrative Rule Set - Check State Top-Up)",
            isThresholdVerifiedOfficial = false,
            isRuleStaleWarning = true,
            ruleVersion = "v1.1.0-illustrative",
            rootRuleGroup = RuleGroup(
                combinator = LogicalCombinator.ALL,
                conditions = listOf(
                    RuleCondition(
                        id = "ignoaps_age",
                        field = "age",
                        operator = RuleOperator.GTE,
                        targetValue = 60,
                        descriptionEn = "Applicant must be 60 years of age or older",
                        descriptionHi = "आवेदक की आयु 60 वर्ष या उससे अधिक होनी चाहिए"
                    ),
                    RuleCondition(
                        id = "ignoaps_bpl",
                        field = "bplStatus",
                        operator = RuleOperator.EQ,
                        targetValue = "yes",
                        descriptionEn = "Applicant household must belong to Below Poverty Line (BPL) category",
                        descriptionHi = "परिवार गरीबी रेखा से नीचे (BPL) सूची में होना चाहिए"
                    ),
                    RuleCondition(
                        id = "ignoaps_income",
                        field = "annualIncome",
                        operator = RuleOperator.LTE,
                        targetValue = 60000,
                        descriptionEn = "Annual household income within illustrative threshold (with state overrides)",
                        descriptionHi = "वार्षिक पारिवारिक आय निर्धारित सीमा के भीतर होनी चाहिए"
                    )
                )
            ),
            stateOverrideIncomeCap = mapOf(
                "Uttar Pradesh" to 56460,
                "Bihar" to 60000,
                "Maharashtra" to 50000,
                "Madhya Pradesh" to 48000,
                "Rajasthan" to 48000
            ),
            requiredDocuments = listOf(
                DocumentRequirement(
                    id = "doc_age_proof",
                    nameEn = "Age Proof Certificate (Birth Certificate / School Leaving / Voter ID / Medical Board Age Certificate)",
                    nameHi = "आयु प्रमाण पत्र (जन्म प्रमाण पत्र / स्कूल प्रमाण पत्र / मतदाता पहचान पत्र / चिकित्सा आयु प्रमाण)",
                    mandatory = true,
                    alternativesEn = "PAN Card or Gazette / Gram Panchayat Age Verification Certificate",
                    alternativesHi = "पैन कार्ड या ग्राम पंचायत द्वारा सत्यापित आयु प्रमाण पत्र",
                    howToObtainEn = "Obtain from Municipal Corporation, Gram Panchayat, or Government Hospital.",
                    howToObtainHi = "नगर निगम, ग्राम पंचायत या सरकारी अस्पताल से प्राप्त करें।"
                ),
                DocumentRequirement(
                    id = "doc_bpl_card",
                    nameEn = "BPL Ration Card / SECC-2011 BPL Listing Certificate",
                    nameHi = "बीपीएल (BPL) राशन कार्ड / बीपीएल सूची प्रमाण पत्र",
                    mandatory = true,
                    alternativesEn = "Antyodaya Anna Yojana (AAY) Card or State Social Welfare Income/BPL Certificate",
                    alternativesHi = "अंत्योदय अन्न योजना कार्ड या समाज कल्याण विभाग का प्रमाण पत्र",
                    howToObtainEn = "Issued by Block Development Officer (BDO) or Food & Civil Supplies office.",
                    howToObtainHi = "खंड विकास अधिकारी (BDO) या खाद्य एवं रसद विभाग कार्यालय से प्राप्त करें।"
                ),
                DocumentRequirement(
                    id = "doc_pension_bank",
                    nameEn = "Single-Holder Savings Bank / Post Office Account Passbook",
                    nameHi = "बैंक या डाकघर बचत खाता पासबुक",
                    mandatory = true,
                    alternativesEn = "Post Office Savings Account Passbook",
                    alternativesHi = "डाकघर बचत खाता पासबुक",
                    howToObtainEn = "Open a zero-balance Jan Dhan account at any nationalized bank or Post Office.",
                    howToObtainHi = "किसी भी राष्ट्रीयकृत बैंक या डाकघर में जन-धन खाता खुलवाएं।"
                )
            ),
            applicationInstructionsEn = "1. Submit form online via State Pension Portal / UMANG app or offline at Block Development Office (BDO) in rural areas / SDM office in urban areas.\n2. Attach BPL proof and age proof.",
            applicationInstructionsHi = "1. ग्रामीण क्षेत्र में खंड विकास अधिकारी (BDO) कार्यालय या शहरी क्षेत्र में समाज कल्याण कार्यालय / राज्य पेंशन पोर्टल पर आवेदन करें।\n2. बीपीएल प्रमाण और आयु प्रमाण संलग्न करें।",
            cscOfficeGuidanceEn = "Visit your Gram Panchayat Secretary, Block Social Welfare Officer, or nearest CSC.",
            cscOfficeGuidanceHi = "ग्राम पंचायत सचिव, समाज कल्याण अधिकारी या नजदीकी जन सेवा केंद्र (CSC) से मिलें।"
        ),

        // 3. PMUY
        GovernmentScheme(
            id = "PMUY",
            code = "PMUY",
            nameEn = "PMUY (Pradhan Mantri Ujjwala Yojana 2.0)",
            nameHi = "प्रधानमंत्री उज्ज्वला योजना 2.0 (PMUY)",
            category = "Clean Cooking Fuel / स्वच्छ रसोई ईंधन",
            benefitSummaryEn = "Illustrative: Deposit-free LPG connection + first refill & stove assistance for eligible adult women from poor households.",
            benefitSummaryHi = "उदाहरणार्थ: गरीब परिवारों की वयस्क महिलाओं को बिना जमानत राशि के मुफ्त एलपीजी कनेक्शन एवं पहली रिफिल/चूल्हा सहायता।",
            descriptionEn = "Provides clean cooking fuel (LPG) connections to adult women from deprived and BPL/SECC households having no existing LPG connection.",
            descriptionHi = "वंचित और बीपीएल परिवारों की वयस्क महिलाओं को धुआं-मुक्त रसोई के लिए एलपीजी गैस कनेक्शन प्रदान करने की योजना।",
            officialSourceTitle = "PM Ujjwala Yojana Official Portal (MoPNG)",
            officialSourceUrl = "https://www.pmuy.gov.in",
            verificationDate = "2026-02-01 (Illustrative Rule Set - Verify on Portal)",
            isThresholdVerifiedOfficial = false,
            isRuleStaleWarning = false,
            ruleVersion = "v2.0.1-illustrative",
            rootRuleGroup = RuleGroup(
                combinator = LogicalCombinator.ALL,
                conditions = listOf(
                    RuleCondition(
                        id = "pmuy_gender",
                        field = "gender",
                        operator = RuleOperator.EQ,
                        targetValue = "female",
                        descriptionEn = "Applicant must be an adult woman",
                        descriptionHi = "आवेदक महिला होनी चाहिए"
                    ),
                    RuleCondition(
                        id = "pmuy_age",
                        field = "age",
                        operator = RuleOperator.GTE,
                        targetValue = 18,
                        descriptionEn = "Applicant woman must be at least 18 years old",
                        descriptionHi = "महिला आवेदक की आयु कम से कम 18 वर्ष होनी चाहिए"
                    ),
                    RuleCondition(
                        id = "pmuy_no_lpg",
                        field = "hasLpgConnection",
                        operator = RuleOperator.EQ,
                        targetValue = "no",
                        descriptionEn = "There must be no existing LPG connection in the household",
                        descriptionHi = "परिवार में पहले से कोई एलपीजी गैस कनेक्शन नहीं होना चाहिए"
                    )
                ),
                nestedGroups = listOf(
                    RuleGroup(
                        combinator = LogicalCombinator.ANY,
                        conditions = listOf(
                            RuleCondition(
                                id = "pmuy_bpl",
                                field = "bplStatus",
                                operator = RuleOperator.EQ,
                                targetValue = "yes",
                                descriptionEn = "Household holds BPL / Antyodaya card",
                                descriptionHi = "परिवार के पास BPL / अंत्योदय कार्ड है"
                            ),
                            RuleCondition(
                                id = "pmuy_sc_st",
                                field = "socialCategory",
                                operator = RuleOperator.IN,
                                targetValue = listOf("sc", "st", "obc"),
                                descriptionEn = "Belongs to eligible deprived / SC / ST / Most Backward category",
                                descriptionHi = "अनुसूचित जाति / जनजाति / पात्र वंचित वर्ग से संबंधित"
                            )
                        )
                    )
                )
            ),
            requiredDocuments = listOf(
                DocumentRequirement(
                    id = "doc_ration_deprivation",
                    nameEn = "Ration Card / State Family Register / Deprivation Self-Declaration (Annexure-I for migrants)",
                    nameHi = "राशन कार्ड / परिवार रजिस्टर नकल / प्रवासी स्व-घोषणा पत्र (अनुलग्नक-I)",
                    mandatory = true,
                    alternativesEn = "BPL List Certificate or Antyodaya Card",
                    alternativesHi = "बीपीएल सूची प्रमाण पत्र या अंत्योदय कार्ड",
                    howToObtainEn = "Available from Food & Civil Supplies portal or local LPG distributor (Annexure-I).",
                    howToObtainHi = "खाद्य विभाग पोर्टल या नजदीकी गैस एजेंसी से प्राप्त करें।"
                ),
                DocumentRequirement(
                    id = "doc_woman_bank",
                    nameEn = "Bank Account Passbook in the Woman Applicant's Name",
                    nameHi = "महिला आवेदक के नाम पर बैंक पासबुक",
                    mandatory = true,
                    alternativesEn = "Jan Dhan Account Passbook with IFSC",
                    alternativesHi = "जन-धन बैंक खाता पासबुक (IFSC सहित)",
                    howToObtainEn = "Open at any bank branch or Banking Correspondent (Bank Mitra).",
                    howToObtainHi = "किसी भी बैंक शाखा या बैंक मित्र केंद्र से प्राप्त करें।"
                )
            ),
            applicationInstructionsEn = "1. Apply online at pmuy.gov.in choosing Indane, Bharatgas, or HP Gas distributor.\n2. Or visit your nearest LPG distributor office with family ration card and bank passbook.",
            applicationInstructionsHi = "1. pmuy.gov.in पर ऑनलाइन आवेदन करें और नजदीकी इंडेन, भारतगैस या एचपी गैस एजेंसी चुनें।\n2. या राशन कार्ड और बैंक पासबुक लेकर नजदीकी एलपीजी वितरक कार्यालय जाएं।",
            cscOfficeGuidanceEn = "Visit any authorized Indane / HP / Bharatgas agency or Common Service Centre (CSC).",
            cscOfficeGuidanceHi = "किसी भी अधिकृत गैस एजेंसी या नजदीकी जन सेवा केंद्र (CSC) पर संपर्क करें।"
        ),

        // 4. PM-JAY
        GovernmentScheme(
            id = "PM-JAY",
            code = "PM-JAY",
            nameEn = "Ayushman Bharat PM-JAY (Pradhan Mantri Jan Arogya Yojana)",
            nameHi = "आयुष्मान भारत पीएम-जय (प्रधानमंत्री जन आरोग्य योजना)",
            category = "Health Insurance / स्वास्थ्य बीमा",
            benefitSummaryEn = "Illustrative: Cashless secondary and tertiary hospital cover up to ₹5 Lakh per family per year at empanelled hospitals.",
            benefitSummaryHi = "उदाहरणार्थ: सूचीबद्ध सरकारी और निजी अस्पतालों में प्रति परिवार प्रति वर्ष ₹5 लाख तक का कैशलेस इलाज।",
            descriptionEn = "National health protection scheme covering hospitalization expenses for vulnerable families and senior citizens aged 70+.",
            descriptionHi = "गरीब एवं वंचित परिवारों तथा 70+ वरिष्ठ नागरिकों के लिए कैशलेस अस्पताल उपचार प्रदान करने वाली स्वास्थ्य सुरक्षा योजना।",
            officialSourceTitle = "National Health Authority (NHA) - Beneficiary Portal",
            officialSourceUrl = "https://beneficiary.nha.gov.in",
            verificationDate = "2026-01-25 (Illustrative Rule Set - SECC/State Lookup Required)",
            isThresholdVerifiedOfficial = false,
            isRuleStaleWarning = false,
            ruleVersion = "v1.4.0-illustrative",
            rootRuleGroup = RuleGroup(
                combinator = LogicalCombinator.ALL,
                conditions = listOf(
                    RuleCondition(
                        id = "pmjay_external_secc",
                        field = "secc_or_state_beneficiary_list",
                        operator = RuleOperator.EXTERNAL_LOOKUP,
                        descriptionEn = "Requires external verification in SECC-2011 / State NFSA / 70+ Senior Citizen PM-JAY Registry",
                        descriptionHi = "SECC-2011 / राशन कार्ड / 70+ वरिष्ठ नागरिक पीएम-जय सूची में बाहरी सत्यापन आवश्यक है",
                        externalGuidanceEn = "Because PM-JAY eligibility depends on official SECC-2011 / NFSA household inclusion or the 70+ Senior Citizen Vaya Vandana registry, check your family name on beneficiary.nha.gov.in or at an Ayushman Arogya Mandir.",
                        externalGuidanceHi = "चूंकि पीएम-जय पात्रता आधिकारिक SECC-2011 / राशन कार्ड डेटाबेस या 70+ वय वंदना सूची पर निर्भर करती है, कृपया beneficiary.nha.gov.in या आयुष्मान आरोग्य मंदिर पर अपना नाम जांचें।"
                    )
                ),
                nestedGroups = listOf(
                    RuleGroup(
                        combinator = LogicalCombinator.ANY,
                        conditions = listOf(
                            RuleCondition(
                                id = "pmjay_bpl",
                                field = "bplStatus",
                                operator = RuleOperator.EQ,
                                targetValue = "yes",
                                descriptionEn = "Household holds BPL / NFSA card",
                                descriptionHi = "परिवार के पास BPL / राष्ट्रीय खाद्य सुरक्षा कार्ड है"
                            ),
                            RuleCondition(
                                id = "pmjay_senior_70",
                                field = "age",
                                operator = RuleOperator.GTE,
                                targetValue = 70,
                                descriptionEn = "Senior citizen aged 70 years or older (Ayushman Vaya Vandana)",
                                descriptionHi = "70 वर्ष या अधिक आयु के वरिष्ठ नागरिक (आयुष्मान वय वंदना)"
                            ),
                            RuleCondition(
                                id = "pmjay_low_income",
                                field = "annualIncome",
                                operator = RuleOperator.LTE,
                                targetValue = 120000,
                                descriptionEn = "Low household income within state health scheme threshold",
                                descriptionHi = "कम वार्षिक पारिवारिक आय"
                            )
                        )
                    )
                )
            ),
            requiredDocuments = listOf(
                DocumentRequirement(
                    id = "doc_family_id",
                    nameEn = "NFSA Ration Card / Parivar Pehchan Patra / PM Letter with Family ID",
                    nameHi = "राशन कार्ड / परिवार पहचान पत्र / पीएम-जय परिवार आईडी पत्र",
                    mandatory = true,
                    alternativesEn = "State Health Card or BOCW Construction Worker Registration Card",
                    alternativesHi = "राज्य स्वास्थ्य कार्ड या निर्माण श्रमिक पंजीकरण कार्ड",
                    howToObtainEn = "Check on beneficiary.nha.gov.in or visit Ayushman Mitra helpdesk at any government hospital.",
                    howToObtainHi = "beneficiary.nha.gov.in पर जांचें या किसी भी सरकारी अस्पताल में 'आयुष्मान मित्र' हेल्पडेस्क पर जाएं।"
                ),
                DocumentRequirement(
                    id = "doc_personal_id",
                    nameEn = "Government Photo Identity & Age Proof",
                    nameHi = "सरकारी फोटो पहचान और आयु प्रमाण पत्र",
                    mandatory = true,
                    alternativesEn = "Voter ID, PAN Card, or Senior Citizen Card",
                    alternativesHi = "मतदाता पहचान पत्र, पैन कार्ड या वरिष्ठ नागरिक कार्ड",
                    howToObtainEn = "Carry original photo ID to the Ayushman Mitra desk for e-KYC card generation.",
                    howToObtainHi = "आयुष्मान कार्ड बनवाने के लिए मूल पहचान पत्र साथ ले जाएं।"
                )
            ),
            applicationInstructionsEn = "1. Check household inclusion at beneficiary.nha.gov.in.\n2. Visit any empanelled hospital's Ayushman Mitra desk or CSC to generate your Ayushman Card.",
            applicationInstructionsHi = "1. beneficiary.nha.gov.in पर अपने परिवार का नाम जांचें।\n2. आयुष्मान कार्ड बनवाने के लिए सूचीबद्ध अस्पताल के 'आयुष्मान मित्र' डेस्क या नजदीकी CSC पर जाएं।",
            cscOfficeGuidanceEn = "Visit the Pradhan Mantri Arogya Mitra desk at your District Hospital, CHC, or PHC.",
            cscOfficeGuidanceHi = "जिला अस्पताल, सामुदायिक स्वास्थ्य केंद्र (CHC) या प्राथमिक स्वास्थ्य केंद्र (PHC) में आरोग्य मित्र से मिलें।"
        ),

        // 5. APY
        GovernmentScheme(
            id = "APY",
            code = "APY",
            nameEn = "APY (Atal Pension Yojana)",
            nameHi = "अटल पेंशन योजना (APY)",
            category = "Contributory Pension / अंशदायी पेंशन",
            benefitSummaryEn = "Illustrative: Guaranteed monthly pension of ₹1,000 to ₹5,000 after age 60 based on monthly contribution by subscribers aged 18–40.",
            benefitSummaryHi = "उदाहरणार्थ: 18 से 40 वर्ष की आयु में जुड़ने पर 60 वर्ष की आयु के बाद ₹1,000 से ₹5,000 तक की गारंटीड मासिक पेंशन।",
            descriptionEn = "Government-backed contributory pension scheme for unorganized sector workers aged between 18 and 40 years who are not income tax payers.",
            descriptionHi = "असंगठित क्षेत्र के कामगारों (18 से 40 वर्ष) के लिए वृद्धावस्था आय सुरक्षा सुनिश्चित करने वाली अंशदायी पेंशन योजना।",
            officialSourceTitle = "Pension Fund Regulatory and Development Authority (PFRDA / NPS Trust)",
            officialSourceUrl = "https://www.npscra.nsdl.co.in",
            verificationDate = "2026-01-10 (Illustrative Rule Set - Verify on Portal)",
            isThresholdVerifiedOfficial = false,
            isRuleStaleWarning = false,
            ruleVersion = "v1.3.0-illustrative",
            rootRuleGroup = RuleGroup(
                combinator = LogicalCombinator.ALL,
                conditions = listOf(
                    RuleCondition(
                        id = "apy_age_between",
                        field = "age",
                        operator = RuleOperator.BETWEEN,
                        targetValue = 18,
                        secondaryTargetValue = 40,
                        descriptionEn = "Entry age must be between 18 and 40 years (inclusive)",
                        descriptionHi = "आवेदक की आयु 18 से 40 वर्ष के बीच होनी चाहिए"
                    ),
                    RuleCondition(
                        id = "apy_bank",
                        field = "hasBankAccount",
                        operator = RuleOperator.EQ,
                        targetValue = "yes",
                        descriptionEn = "Must have a savings bank account or post office savings account for auto-debit",
                        descriptionHi = "ऑटो-डेबिट के लिए बचत बैंक खाता या डाकघर खाता होना आवश्यक है"
                    ),
                    RuleCondition(
                        id = "apy_not_taxpayer",
                        field = "isIncomeTaxPayer",
                        operator = RuleOperator.EQ,
                        targetValue = "no",
                        descriptionEn = "Must not be an income tax payer (effective Oct 1, 2022)",
                        descriptionHi = "आयकर दाता नहीं होना चाहिए (1 अक्टूबर 2022 से लागू नियम)"
                    )
                )
            ),
            requiredDocuments = listOf(
                DocumentRequirement(
                    id = "doc_apy_bank",
                    nameEn = "Savings Bank Account / Post Office Account Passbook (for monthly auto-debit mandate)",
                    nameHi = "बचत बैंक खाता या डाकघर पासबुक (मासिक अंशदान ऑटो-डेबिट हेतु)",
                    mandatory = true,
                    alternativesEn = "Netbanking / Mobile Banking e-APY registration",
                    alternativesHi = "नेटबैंकिंग या मोबाइल बैंकिंग के माध्यम से e-APY पंजीकरण",
                    howToObtainEn = "Use your existing savings bank account at any bank or Post Office.",
                    howToObtainHi = "अपने मौजूदा बैंक या डाकघर बचत खाते का उपयोग करें।"
                ),
                DocumentRequirement(
                    id = "doc_apy_age_nominee",
                    nameEn = "Age Proof & Nominee Details (Spouse / Family Member Name & DOB)",
                    nameHi = "आयु प्रमाण और नॉमिनी (पति/पत्नी या परिवार के सदस्य) का विवरण",
                    mandatory = true,
                    alternativesEn = "Birth Certificate, Voter ID, Driving License, or PAN Card",
                    alternativesHi = "जन्म प्रमाण पत्र, वोटर आईडी, ड्राइविंग लाइसेंस या पैन कार्ड",
                    howToObtainEn = "Provide nominee details on the APY registration form at your bank branch.",
                    howToObtainHi = "अपनी बैंक शाखा में APY पंजीकरण फॉर्म में नॉमिनी का विवरण भरें।"
                )
            ),
            applicationInstructionsEn = "1. Approach the bank branch or post office where you hold your savings account, or use e-APY in your bank app.\n2. Choose pension slab (₹1,000 to ₹5,000/month) and sign auto-debit mandate.",
            applicationInstructionsHi = "1. जिस बैंक या डाकघर में आपका बचत खाता है वहां जाएं, या बैंक ऐप में e-APY खोलें।\n2. अपनी पेंशन राशि (₹1,000 से ₹5,000/माह) चुनें और ऑटो-डेबिट फॉर्म भरें।",
            cscOfficeGuidanceEn = "Visit your bank branch, Post Office, or Banking Correspondent (Bank Mitra / CSC).",
            cscOfficeGuidanceHi = "अपनी बैंक शाखा, डाकघर या नजदीकी बैंक मित्र / CSC केंद्र से संपर्क करें।"
        )
    )

    fun getSchemeById(schemeId: String): GovernmentScheme? {
        return initialFiveSchemes.find { it.id.equals(schemeId, ignoreCase = true) || it.code.equals(schemeId, ignoreCase = true) }
    }

    /**
     * Evaluates a citizen profile against a specific government scheme deterministically.
     * Pure, synchronous, I/O-free function. Same input always produces the exact same output.
     */
    fun evaluateScheme(
        profile: CitizenProfile,
        scheme: GovernmentScheme,
        currentYear: Int = currentReferenceYear()
    ): EligibilityEvaluationResult {
        val conditionResults = mutableListOf<ConditionResult>()
        val missingFields = linkedSetOf<String>()
        val externalGuidanceEn = mutableListOf<String>()
        val externalGuidanceHi = mutableListOf<String>()
        var stateOverrideNote: String? = null

        val outcome = evaluateGroup(
            group = scheme.rootRuleGroup,
            profile = profile,
            scheme = scheme,
            currentYear = currentYear,
            conditionResults = conditionResults,
            missingFields = missingFields,
            externalGuidanceEn = externalGuidanceEn,
            externalGuidanceHi = externalGuidanceHi,
            onStateOverrideUsed = { note -> stateOverrideNote = note }
        )

        return EligibilityEvaluationResult(
            schemeId = scheme.id,
            schemeCode = scheme.code,
            ruleVersion = scheme.ruleVersion,
            outcome = outcome,
            conditionResults = conditionResults,
            missingFields = missingFields.toList(),
            externalLookupGuidanceEn = externalGuidanceEn,
            externalLookupGuidanceHi = externalGuidanceHi,
            stateOverrideApplied = stateOverrideNote,
            evaluatedAtEpochMs = 0L // Deterministic value for pure comparisons
        )
    }

    fun evaluateAllSchemes(
        profile: CitizenProfile,
        currentYear: Int = currentReferenceYear()
    ): List<EligibilityEvaluationResult> {
        return initialFiveSchemes.map { scheme ->
            evaluateScheme(profile, scheme, currentYear)
        }
    }

    private fun evaluateGroup(
        group: RuleGroup,
        profile: CitizenProfile,
        scheme: GovernmentScheme,
        currentYear: Int,
        conditionResults: MutableList<ConditionResult>,
        missingFields: MutableSet<String>,
        externalGuidanceEn: MutableList<String>,
        externalGuidanceHi: MutableList<String>,
        onStateOverrideUsed: (String) -> Unit
    ): EligibilityOutcome {
        val outcomes = mutableListOf<EligibilityOutcome>()

        for (condition in group.conditions) {
            val result = evaluateCondition(
                condition = condition,
                profile = profile,
                scheme = scheme,
                currentYear = currentYear,
                missingFields = missingFields,
                externalGuidanceEn = externalGuidanceEn,
                externalGuidanceHi = externalGuidanceHi,
                onStateOverrideUsed = onStateOverrideUsed
            )
            conditionResults.add(result)
            outcomes.add(result.outcome)
        }

        for (nested in group.nestedGroups) {
            val nestedOutcome = evaluateGroup(
                group = nested,
                profile = profile,
                scheme = scheme,
                currentYear = currentYear,
                conditionResults = conditionResults,
                missingFields = missingFields,
                externalGuidanceEn = externalGuidanceEn,
                externalGuidanceHi = externalGuidanceHi,
                onStateOverrideUsed = onStateOverrideUsed
            )
            outcomes.add(nestedOutcome)
        }

        return when (group.combinator) {
            LogicalCombinator.ALL -> {
                when {
                    outcomes.any { it == EligibilityOutcome.NOT_ELIGIBLE } -> EligibilityOutcome.NOT_ELIGIBLE
                    outcomes.any { it == EligibilityOutcome.UNKNOWN } -> EligibilityOutcome.UNKNOWN
                    else -> EligibilityOutcome.ELIGIBLE
                }
            }
            LogicalCombinator.ANY -> {
                when {
                    outcomes.any { it == EligibilityOutcome.ELIGIBLE } -> EligibilityOutcome.ELIGIBLE
                    outcomes.isNotEmpty() && outcomes.all { it == EligibilityOutcome.NOT_ELIGIBLE } -> EligibilityOutcome.NOT_ELIGIBLE
                    else -> EligibilityOutcome.UNKNOWN
                }
            }
        }
    }

    private fun evaluateCondition(
        condition: RuleCondition,
        profile: CitizenProfile,
        scheme: GovernmentScheme,
        currentYear: Int,
        missingFields: MutableSet<String>,
        externalGuidanceEn: MutableList<String>,
        externalGuidanceHi: MutableList<String>,
        onStateOverrideUsed: (String) -> Unit
    ): ConditionResult {
        // 1. Handle EXTERNAL_LOOKUP first
        if (condition.operator == RuleOperator.EXTERNAL_LOOKUP) {
            if (condition.externalGuidanceEn.isNotBlank()) {
                externalGuidanceEn.add(condition.externalGuidanceEn)
            }
            if (condition.externalGuidanceHi.isNotBlank()) {
                externalGuidanceHi.add(condition.externalGuidanceHi)
            }
            missingFields.add(condition.field)
            return ConditionResult(
                conditionId = condition.id,
                field = condition.field,
                outcome = EligibilityOutcome.UNKNOWN,
                reasonEn = "External beneficiary lookup required: ${condition.descriptionEn}",
                reasonHi = "बाहरी लाभार्थी सूची में जांच आवश्यक: ${condition.descriptionHi}"
            )
        }

        // 2. Check pending voice confirmation for age
        if (condition.field == "age" && !profile.ageConfirmedViaVoice) {
            missingFields.add("age_voice_confirmation")
            return ConditionResult(
                conditionId = condition.id,
                field = "age",
                outcome = EligibilityOutcome.UNKNOWN,
                reasonEn = "Spoken age requires explicit Haan/Nahi confirmation before final eligibility decision.",
                reasonHi = "आवाज़ से बताई गई उम्र की 'हाँ/नहीं' पुष्टि आवश्यक है।"
            )
        }

        // 3. Extract field value
        val rawValue: Any? = when (condition.field) {
            "age" -> profile.resolveAge(currentYear = currentYear).takeIf { it in 0..120 }
            "gender" -> profile.gender.takeIf { it != "unknown" && it.isNotBlank() }
            "state" -> profile.state.takeIf { it.isNotBlank() }
            "district" -> profile.district.takeIf { it.isNotBlank() }
            "residenceType" -> profile.residenceType.takeIf { it != "unknown" && it.isNotBlank() }
            "annualIncome" -> profile.annualIncome.takeIf { it >= 0 }
            "occupation" -> profile.occupation.takeIf { it != "unknown" && it.isNotBlank() }
            "bplStatus" -> profile.bplStatus.takeIf { it != "unknown" && it.isNotBlank() }
            "socialCategory" -> profile.socialCategory.takeIf { it != "unknown" && it.isNotBlank() }
            "hasBankAccount" -> profile.hasBankAccount.takeIf { it != "unknown" && it.isNotBlank() }
            "hasLpgConnection" -> profile.hasLpgConnection.takeIf { it != "unknown" && it.isNotBlank() }
            "ownsAgriculturalLand" -> profile.ownsAgriculturalLand.takeIf { it != "unknown" && it.isNotBlank() }
            "isIncomeTaxPayer" -> profile.isIncomeTaxPayer.takeIf { it != "unknown" && it.isNotBlank() }
            "familySize" -> profile.familySize.takeIf { it > 0 }
            else -> null
        }

        if (rawValue == null) {
            missingFields.add(condition.field)
            return ConditionResult(
                conditionId = condition.id,
                field = condition.field,
                outcome = EligibilityOutcome.UNKNOWN,
                reasonEn = "Missing information for '${condition.field}' (${condition.descriptionEn})",
                reasonHi = "'${condition.field}' की जानकारी उपलब्ध नहीं है (${condition.descriptionHi})"
            )
        }

        // Apply state override for annualIncome if defined on scheme
        var effectiveTarget: Any? = condition.targetValue
        if (condition.field == "annualIncome" && scheme.stateOverrideIncomeCap.containsKey(profile.state)) {
            val stateCap = scheme.stateOverrideIncomeCap[profile.state]!!
            effectiveTarget = stateCap
            onStateOverrideUsed("${profile.state} state threshold override applied: ₹$stateCap")
        }

        val passed: Boolean = when (condition.operator) {
            RuleOperator.EQ -> rawValue.toString().equals(effectiveTarget?.toString(), ignoreCase = true)
            RuleOperator.NEQ -> !rawValue.toString().equals(effectiveTarget?.toString(), ignoreCase = true)
            RuleOperator.LT -> compareNumbers(rawValue, effectiveTarget) < 0
            RuleOperator.LTE -> compareNumbers(rawValue, effectiveTarget) <= 0
            RuleOperator.GT -> compareNumbers(rawValue, effectiveTarget) > 0
            RuleOperator.GTE -> compareNumbers(rawValue, effectiveTarget) >= 0
            RuleOperator.BETWEEN -> {
                val lowerCmp = compareNumbers(rawValue, effectiveTarget)
                val upperCmp = compareNumbers(rawValue, condition.secondaryTargetValue)
                lowerCmp >= 0 && upperCmp <= 0
            }
            RuleOperator.IN -> {
                val list = (effectiveTarget as? Iterable<*>)?.map { it.toString().lowercase() } ?: emptyList()
                rawValue.toString().lowercase() in list
            }
            RuleOperator.NOT_IN -> {
                val list = (effectiveTarget as? Iterable<*>)?.map { it.toString().lowercase() } ?: emptyList()
                rawValue.toString().lowercase() !in list
            }
            RuleOperator.EXISTS -> true
            RuleOperator.EXTERNAL_LOOKUP -> false
        }

        return if (passed) {
            ConditionResult(
                conditionId = condition.id,
                field = condition.field,
                outcome = EligibilityOutcome.ELIGIBLE,
                reasonEn = "Met: ${condition.descriptionEn} (Value: $rawValue)",
                reasonHi = "पूरा हुआ: ${condition.descriptionHi} (मान: $rawValue)"
            )
        } else {
            ConditionResult(
                conditionId = condition.id,
                field = condition.field,
                outcome = EligibilityOutcome.NOT_ELIGIBLE,
                reasonEn = "Not met: ${condition.descriptionEn} (Current: $rawValue, Required: $effectiveTarget${condition.secondaryTargetValue?.let { "..$it" } ?: ""})",
                reasonHi = "पूरा नहीं हुआ: ${condition.descriptionHi} (वर्तमान: $rawValue, आवश्यक: $effectiveTarget${condition.secondaryTargetValue?.let { "..$it" } ?: ""})"
            )
        }
    }

    private fun compareNumbers(actual: Any, target: Any?): Int {
        val a = (actual as? Number)?.toDouble() ?: actual.toString().toDoubleOrNull() ?: 0.0
        val b = (target as? Number)?.toDouble() ?: target?.toString()?.toDoubleOrNull() ?: 0.0
        return a.compareTo(b)
    }
}
