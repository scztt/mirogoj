VoicePartial {
	classvar <partialSD;
	var server, <group, <synth;
	
		// no latency on loading instance, woo hoo!!
	*initClass {
		partialSD = CtkNoteObject( 
			SynthDef(\ats_flt, {arg in, out=0, freq, paramp, ampcntl, vibcntl, gate=1,
				vib_rate_min=4, vib_rate_max=8, vib_depth=0.006, vib_start_min=1.5, vib_start_max=2.5; // vib args
				var paraenv, bwenv, fenv, sig, f_dev, vibonset, vib;
					SeedMaster.ugen;
					paraenv = EnvGen.kr(
						Control.names(\parampenv).kr(Env.newClear(2).asArray), gate);
					bwenv = EnvGen.kr(
						Control.names(\bandwenv).kr(Env.newClear(2).asArray), gate);
					fenv = EnvGen.kr(
						Control.names(\freqenv).kr(Env.newClear(2).asArray), gate);

					sig = In.ar(in, 1);
					f_dev = vib_depth * fenv;
					vibonset = XLine.kr(0.01, 1, Rand.new(vib_start_min, vib_start_max));
					vib = SinOsc.ar( LFNoise1.kr.range( vib_rate_min, vib_rate_max ), 0, f_dev) * vibonset;
					Out.ar(out,
						Resonz.ar(sig, 
							fenv + ( vib * vibcntl ), 
							//fenv,
							bwenv, paraenv * ampcntl));
				})
	 		)
	}

	*new { | server, group, inBus, outBus, freqenv, parampenv, bwenv, ampcntlbus, vibcntlbus |		^super.newCopyArgs( server, group )
			.init(inBus, outBus, freqenv, parampenv, bwenv, ampcntlbus, vibcntlbus, group)
	}

	init { | inBus, outBus, freqenv, parampenv, bwenv, ampcntlbus, vibcntlbus, thisGroup |
		//[thisProcess.interpreter.g = thisGroup, "Group!"].postln;
		synth = partialSD.new( target: thisGroup, addAction: \tail, server:server )
			.randSeed_(SeedMaster.seed)
			.in_( inBus )
			.out_( outBus )
			.freqenv_( freqenv )
			.parampenv_( parampenv )
			.bandwenv_( bwenv )
			.ampcntl_(ampcntlbus)
			.vibcntl_(vibcntlbus)
				// for getter for Voice - expects 2 brkpnts
			.freq_( freqenv.asArray[4] )
				// for getter for Voice
			.paramp_( parampenv.asArray[4] );
		CmdPeriod.add({ this.free })
	}
	
	play {
		if( synth.isPlaying.not, {
			synth.play(group);
		});
	}

	free { | time=0.0 |
		
		if( synth.isPlaying, { synth.free( time ) })
	}
	
	outbus { ^synth.out }
	outbus_{ | argBus | synth.out_(argBus) }
	
	paramp { ^synth.paramp }
	paramp_{ | argAmp | synth.paramp_(argAmp) }
	
	freq { ^synth.freq }
	freq_{ | argFreq | synth.freq_(argFreq) }
	
	freqenv_{ | env | 
		(synth.gate==0).if(
			{ synth.freqenv_( env , 0.05) },
			{"gate isn't closed yet-freqenv".postln});
	}
	
	parampenv_{ | env |
		(synth.gate==0).if(
			{ synth.parampenv_( env , 0.05) },
			{"gate isn't closed yet-parampenv".postln});
	}
	
	bwenv_{ | env |
		(synth.gate==0).if(
			{ synth.bandwenv_( env , 0.05) },
			{"gate isn't closed yet- bwenv".postln});
	}
		// for batch envelope changes so above methods don't toggle gates many times over
	gate_{ | arggate | 
		(arggate==0).if(
			{ synth.gate_(arggate, 0.0) },
			{ synth.gate_(arggate, 0.1) })
	}

}