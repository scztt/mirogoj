SeedMaster {
	classvar >seedCounter;
	
	*initClass {
		seedCounter = 100;
	}
	
	*ugen {
		| seed |
		var randSeed;
		if( randSeed.notNil, {
			^[ RandID.ir( seed ), RandSeed.ir( seed, seed ) ]
		},{
			randSeed = Control.names([\randSeed]).ir(0);
			^[ RandID.ir( randSeed ), RandSeed.ir( randSeed, randSeed ) ]
		})
	}
	
	*seed {
		^seedCounter = seedCounter+10
	}
}