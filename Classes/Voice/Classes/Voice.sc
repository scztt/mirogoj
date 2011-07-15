// Dependencies: AtsSnapShot, VoicePartial

Voice {
	classvar <ampContSD, <vibContSD;
	var <server, <outBus, <element, <inBus, <group, <ampConBus, <vibConBus, <ampcntlsynth, <vibcntlsynth;
	var <partials, <currSS, <currFreqs, <currAmps;
	var <vbw, <vamp, <transforming, vonset, vcurve;
	
	// put an addaction here for group?
	*new {	| server, element, outBus, snapShot, initamp=1, bw=0.05, onset=5, curve=0 |
		^super.newCopyArgs( server, outBus, element).init(server, snapShot, initamp, bw, onset, curve, element)
	}

	*initClass {
			ampContSD = CtkNoteObject( 
				SynthDef(\amp_control, { arg out=0, lag=5, gate = 1;
					var env;
					env = EnvGen.kr(
						Control.names(\ampenv).kr( Env.newClear(2).asArray ), gate);
					Out.kr(out, env);
					})
		 		);
		 	
		 	vibContSD = CtkNoteObject(
				SynthDef( \vib_conrol, { arg out=0;
					var trig, hold, src;
					SeedMaster.ugen;
					trig = Dust.kr(7.reciprocal);
					hold = TRand.kr(4.5, 9, trig);
					src = LagUD.kr( Trig1.kr( trig, hold), 0.35, 3 );
					Out.kr( out, src );
					})	
				)
		}

	init {
		| argserver, snapShot, initamp, bw, onset, curve, argelement | // pass in a group? addaction for group?
		var snapShotPairs, test;
		server = argserver ?? {Server.default};
		group = CtkGroup.play( server:server, addAction:\tail );
		ampConBus = CtkControl.play( initVal: 1, server: server );
		vibConBus = CtkControl.play( initVal: 0, server: server );
		vonset = onset;
		vcurve = curve;
		element = argelement;
		inBus = element.voiceBus;
		partials = Array.new;
		vbw = bw;
		vamp = initamp;
//		snapShotPairs = snapShot.freqAmpPairs;
//		transforming = true;
		
		ampcntlsynth = ampContSD.new(addAction: \head, target: group)
			.out_(ampConBus)
			.ampenv_( Env([0,1], [vonset], [-3.5]) );
			//.play( group );
		vibcntlsynth = vibContSD.new(addAction: \head, target: group)
			.randSeed_(SeedMaster.seed)
			.out_(vibConBus);
			//.play( group );
		
//		snapShotPairs.do({ | pair |
//			var thisFreq, thisAmp;
//			thisFreq = pair[0];
//			thisAmp = pair[1];
//			this.createVoicePartial(inBus, outBus, 
//				Env([thisFreq, thisFreq],[vonset], vcurve, 1),
//				Env([0, thisAmp],[vonset], vcurve, 1),
//				Env([vbw, vbw],[vonset], vcurve, 1))
//			});
//		SystemClock.sched( onset, 
//			{ "onset finished".postln; transforming = false; nil });
//			// send the element to this Voice's inbus
//		element.sendToVoice;
		
		currSS = snapShot;		
		currFreqs = snapShot.freqs;
		currAmps = snapShot.amps;
		CmdPeriod.add({ ampConBus.free; vibConBus.free; group.deepFree }); // this.free
	}
	
	play { |onset|
		var snapShotPairs;
		onset.notNil.if({ vonset =  onset });
		ampcntlsynth.play( group );
		vibcntlsynth.play( group );

		snapShotPairs = currSS.freqAmpPairs;
		snapShotPairs.do({ | pair |
			var thisFreq, thisAmp;
			thisFreq = pair[0];
			thisAmp = pair[1];
			this.createVoicePartial(inBus, outBus, 
				Env([thisFreq, thisFreq],[vonset], vcurve, 1),
				Env([0, thisAmp],[vonset], vcurve, 1),
				Env([vbw, vbw],[vonset], vcurve, 1))
			});
			
		transforming = true;
		SystemClock.sched( vonset, 
			{ "onset finished".postln; transforming = false });
			// send the element to this Voice's inbus
		element.sendToVoice(onset);
	}
	
	transform {
		| targetSnapShot, partialThresh=200, targetAmp, targetBw, transformTime=20, curve=0 |
		var targetSSPairs, testMatch, list_a, list_b;
		var killPartials, createPartials;
		var transBwEnv, ampcurve, freedelay;
		transforming.if(
			{ "wait for previous transformation to finish".postln},
			{	
				transforming = true;
				killPartials = Array.new;
				createPartials = Array.new;
				transBwEnv = Env([vbw, targetBw], [transformTime], curve, 1);
		
					// Find closest matches, set the arrays for those partials to kill, save, and create
				#list_a, list_b = this.targetFreqs(currSS, targetSnapShot, partialThresh);
					// translate list_a and list_b, into a save, kill, create list
				list_a.do({ | mate, index |
					var transFreqEnv, transParAmpEnv;
					(mate == -1).if(
						{	killPartials = killPartials.add(index) },
						{	// change the freqs and amps of saved partials
							"partial ".post; index.post; " ( ".post; partials[index].freq.post; 
							" ) transforms to partial ".post; mate.post; " ( ".post; 
							targetSnapShot.freqs[mate].post; " )".postln;
		
							server.makeBundle(0.0, {
								ampcntlsynth.gate_( 0, 0.0 );
								( targetAmp > vamp ).if( { ampcurve = 2.7 }, { ampcurve = -2.7 } );
								ampcntlsynth.ampenv_( Env([vamp, targetAmp], [transformTime], ampcurve), 0.05 );
								ampcntlsynth.gate_( 1, 0.1 );
							});
		
							server.makeBundle(0.0, {
								partials[index].gate_(0);
								transFreqEnv = Env([ partials[index].freq, targetSnapShot.freqs[mate] ], 
									[transformTime], curve, 1);
								transParAmpEnv = Env([ partials[index].paramp, targetSnapShot.amps[mate] ], 
									[transformTime], curve, 1);
								partials[index]
									.freqenv_( transFreqEnv )
									.parampenv_( transParAmpEnv )
									.freq_( targetSnapShot.freqs[mate] )
									.paramp_( targetSnapShot.amps[mate] )
									.bwenv_( transBwEnv );
								partials[index].gate_(1);
							})
						})
					});
		
				list_b.do({ | birth, index |
						birth.if({ createPartials = createPartials.add(index) })
					});
		
				freedelay = transformTime + 1; // sloppy, also delay built into gate method above
				server.makeBundle( 0.0, {
					killPartials.sort.reverse.do({ | killDex, index |
						var removedelay;
							"killing partial of freq: ".post;
							partials[killDex].freq.post; " of index: ".post; killDex.postln;
							
							partials[killDex].gate_( 0 );
							partials[killDex].bwenv_( transBwEnv );
							partials[killDex].parampenv_( 
								Env( [partials[killDex].paramp, -400.dbamp], [transformTime], -5.5, 1 ) );
							partials[killDex].gate_( 1 );
							
								// use the releasetime?
							"freeing partial of index: ".post; killDex.postln;
							partials[killDex].free( freedelay );
								// make sure they're freed before removed from the partial list
							removedelay = ( freedelay + 0.1 + (0.05*index) );							
							SystemClock.sched(removedelay, {
									"removing partial from index: ".post; killDex.postln;
									partials.removeAt(killDex);
									nil // so nothing is rescheduled
								})
							/* lookintothis...
										a = TempoClock.new(queueSize: 2048);
											1024.do({arg i;
												a.sched(0.1, {i.postln; nil})
												})
											// free the thread when you are done
											a.stop;
											Or - go crazy and keep rescheduling:
											a = TempoClock.new(queueSize: 2048);
											1024.do({arg i;
												a.sched(0.1, {i.postln; 0.2.rrand(1.0)})
												})
											a.stop;
							*/

						})
				});
				
					// create those that need to be born
				createPartials.do({ | targDex, index |
					var thisFreq, thisAmp;
						thisFreq = targetSnapShot.freqs[targDex];
						thisAmp = targetSnapShot.amps[targDex];
						this.createVoicePartial(inBus, outBus, 
							Env([thisFreq, thisFreq],[transformTime], curve, 1),
							Env([-400.dbamp, thisAmp],[transformTime], 3.5, 1),
							Env([vbw, targetBw],[transformTime], curve, 1));
						"created partial of freq: ".post; targetSnapShot.freqs[targDex].postln;
					});
		
					//	resort the partials array in ascending frequency order
				SystemClock.sched(transformTime + 5, { // wait transformTime + 5 seconds to update, not ideal
						partials = this.reorderPartials(partials);
						currFreqs = targetSnapShot.freqs;
						currAmps = targetSnapShot.amps;
						currSS = targetSnapShot; // wait the max lag time to update this?
						"transformation complete".postln;
						transforming = false;
//						SystemClock.clear; // careful....
					});
				vamp = targetAmp;
				vbw = targetBw;
			});
	}
	
	free { | releaseTime=10 |
		server.makeBundle(0.0, {
			element.removeVoice( releaseTime );
			ampcntlsynth.gate_( 0, 0.0 );
			ampcntlsynth.ampenv_( Env([ vamp, -400.dbamp], [releaseTime], -1.5, 1), 0.05 );
			ampcntlsynth.gate_( 1, 0.1 );
//			group.set(0.0, \gate, 0);
//			group.setn(0.05, \ampscaleenv, Env([ vamp, -400.dbamp], [releaseTime], -5.5, 1).asArray);
//			group.set(0.1, \gate, 1);
			SystemClock.sched(releaseTime + 1.15, { ampConBus.free; vibConBus.free; group.deepFree; "Voice freed".postln });
		})
	}

	killNow {
		ampConBus.free; vibConBus.free; ampcntlsynth.free; vibcntlsynth.free; group.deepFree;
	}
	
	amp_{ | argamp, lag=5 |
		var ampcurve;
		( argamp > vamp ).if( { ampcurve = 2.7 }, { ampcurve = -2.7 } );
		server.makeBundle(0.0, {
			ampcntlsynth.gate_( 0, 0.0 );
			ampcntlsynth.ampenv_( Env([ vamp, argamp], [lag], ampcurve, 1), 0.05 );
			ampcntlsynth.gate_( 1, 0.1 );
		});
		vamp = argamp;
	}	
		// add individual bandwidth transform controls
	bw_{ | argbw, argamp, lag=5, curve=0 |
		var bwenv, bwcurve, ampcurve;
			(argbw > vbw).if( { bwcurve = curve }, { bwcurve = curve.neg } );
			bwenv = Env([ vbw, argbw ], [ lag ], bwcurve, 1); 
			server.makeBundle(0.0, {
				group.set(0.0, \gate, 0);
				group.setn(0.05, \bandwenv, bwenv.asArray);
				argamp.notNil.if({
					(argamp > vamp).if( { ampcurve = 2.7 }, { ampcurve = -2.7 } );
					ampcntlsynth.ampenv_( Env( [vamp, argamp], [lag], ampcurve ) );
					vamp = argamp;
					});
				group.set(0.1, \gate, 1);
			});
			vbw = argbw;
	}

		// create one partial of the voice
	createVoicePartial {
		| inBus, outBus, freqenv, parampenv, bwenv | //ampscaleenv
		var thisPartial;
			thisPartial = VoicePartial.new(this.server, this.group,
				inBus, outBus, freqenv, parampenv, bwenv, ampConBus, vibConBus);
			thisPartial.play;
			partials = partials.add( thisPartial );  // adds to the end of the list
	}

// add inBus and outBus getters/setters?
	
	bubsort { | snapfreqs |	/// JGL
           		var temp, indices;           
		           indices = Array.fill(snapfreqs.size, {arg i; i});
		           for ( 0, snapfreqs.size-2,
		            {     arg i;
		                for ( snapfreqs.size-1, i+1,
		                {    arg j;
		                    if (snapfreqs[indices[j]] < snapfreqs[indices[j-1]],
		                    {    temp = indices[j];
		                        indices[j] = indices[j-1];
		                        indices[j-1] = temp;
		                    })
		                })
		            });
		            ^indices; 
	}	
		
	in_range { 	| val, low, hi |
				((val >= low) && (val <= hi)).if(
						{^true},
						{^false}
					)
	}

	cost_metric {	| v1, v2 |
				^(v1-v2)**2;
	}
	
	engage {		| costs, hubs, wives, start |
				var married, old_hub, top_wife, temp;
				(start >= costs.size).if(
					{ 
//						hubs.postln;
//						wives.postln;
						^[ hubs, wives ]
					}, 
					{	married = false;
						while ( 	{ (married.not) && (costs[start].size != 0) },
								{ 	top_wife = costs[start].removeAt(0);
									if ( wives[top_wife[0]][0] == -1, 
										{ 	// fresh wife
											wives[top_wife[0]] = [start,top_wife[1]];
											hubs[start] = top_wife;
											married = true;
										},
										{	
											if (hubs[wives[top_wife[0]][0]][1] > top_wife[1],
												{    // secondhand wife
											          old_hub = wives[top_wife[0]];
											          hubs[old_hub[0]][0] = -1;
											          wives[top_wife[0]] = [start, top_wife[1]];
											          hubs[start] = top_wife;
											          married = true;
											          temp = this.engage(costs,hubs,wives,old_hub[0]);
//											          temp.postln;
											          hubs = temp[0];
											          wives = temp[1];
											   });
										});
								});
						^this.engage(costs,hubs,wives,start+1);
					})
	}
		
	targetFreqs  {	| ss1, ss2, threshold |		/// JGL's ats_meld
				var list_a, list_b, list_a_inv, list_b_inv,
					costsab, costsba, temp,
					prev_min, newiter, ind, current_cost, 
					break, j, k, indices1, indices2, suma, sumb,
					freqs1, freqs2;
					
				freqs1 = ss1.freqs;
				freqs2 = ss2.freqs;
				// SHOULDN'T BE NECESSARY
					// sort inputs
				indices1 = this.bubsort(freqs1);
				indices2 = this.bubsort(freqs2);
					// setup output lists
				list_a = Array.fill(freqs1.size, {[-1,0.0]});
				list_b = Array.fill(freqs2.size, {[-1,0.0]});					// inverse mapping for lists
				list_a_inv = Array.fill(freqs2.size, {[-1,0.0]});
				list_b_inv = Array.fill(freqs1.size, {[-1,0.0]});
					// construct cost array of list for list_a -> list_b
				costsab = Array.fill(list_a.size, { List.new(list_b.size) });
				prev_min = 0;
				for (0, costsab.size-1,
					{	arg i;
						newiter = true;
						j = prev_min;
						while ({j < freqs2.size},
							{	
								if (this.in_range(freqs2[indices2[j]],freqs1[indices1[i]]-threshold,freqs1[indices1[i]]+threshold),
									{	// optimizing list search based on sorted ordering
										if (newiter,
											{	newiter = false;
												prev_min = j;
											});
										// if in the threshold, add it to the costs in sorted order
										ind = 0;
										break = 0;
										current_cost = this.cost_metric(freqs1[indices1[i]],freqs2[indices2[j]]);
										k = 0;
										while ( {k < costsab[i].size},
											{	if (costsab[i][k][1] > current_cost,
												    { break = 1;
												      k = costsab[i].size;
												    });
												ind = ind + 1;
												k = k + 1;
											});
										costsab[i].insert(ind-break,[j,current_cost]);
									},
									{	// if we've passed the threshold, break
										if (freqs2[indices2[j]] > (freqs1[indices1[i]]+threshold),
											{ j = freqs2.size;
											});
									});
								j = j + 1;
							});
					});
					
				// construct cost array of lists for list_a -> list_b
				costsba = Array.fill(list_b.size, {List.new(list_a.size)});

				prev_min = 0;
				for (0, costsba.size-1,
					{	arg i;
						newiter = true;
						j = prev_min;
						while ({j < freqs1.size},
							{	if (this.in_range(freqs1[indices1[j]],freqs2[indices2[i]]-threshold,freqs2[indices2[i]]+threshold),
									{	// optimizing list search based on sorted ordering
										if (newiter,
											{	newiter = false;
												prev_min = j;
											});
										// if in the threshold, add it to the costs in sorted order
										ind = 0;
										break = 0;
										current_cost = this.cost_metric(freqs2[indices2[i]],freqs1[indices1[j]]);
										k = 0;
										while ( {k < costsba[i].size},
											{	if (costsba[i][k][1] > current_cost,
												    { break = 1;
												      k = costsba[i].size;
												    });
												ind = ind + 1;
												k = k + 1;
											});
										costsba[i].insert(ind-break,[j,current_cost]);
									},
									{	// if we've passed the threshold, break
										if (freqs1[indices1[j]] > (freqs2[indices2[i]]+threshold),
											{ j = freqs1.size;
											});
									});
								j = j + 1;
							});
					});					
				
				"costs calculated".postln;
									
					// engagement ceremonies
				temp = this.engage(costsab,list_a,list_a_inv,0);
				"a final try?".postln;
				list_a = temp[0];
				temp = this.engage(costsba,list_b,list_b_inv,0);
				"ok last".postln;
				list_b = temp[0];
				
					// calculate errors for each optimal case
				suma = [0,0];
				for (0, list_a.size-1,
					{	arg i;
						if ( list_a[i][0] != -1,
							{ 	suma[0] = suma[0] + 1;
								suma[1] = suma[1] + list_a[i][1];
							});
					});
				sumb = [0,0];
				for (0, list_b.size-1,
					{	arg i;
						if ( list_b[i][0] != -1,
							{ 	sumb[0] = sumb[0] + 1;
								sumb[1] = sumb[1] + list_b[i][1];
							});
					});
				
					// setup output based on average error
				//if you'd like it just to be based on the number of matches you can change to this line:
				//if(     (suma[0] > sumb[0]),
				if (	(suma[1]/suma[0]) < (sumb[1]/sumb[0]),
					{	for (0, list_b.size-1,
							{	arg i;
								list_b[i] = true;
							});
						for (0, list_a.size-1,
							{	arg i;
								if ( list_a[i][0] >= 0,
									{	list_b[list_a[i][0]] = false;
									});			
								list_a[i] = list_a[i][0];
							});					
					},
					{	
						for (0, list_a.size-1,
							{ 	arg i;
								list_a[i] = -1;
							});
						for (0, list_b.size-1,
							{ 	arg i;
								if ( list_b[i][0] != -1,
									{ list_a[list_b[i][0]] = i;
									  list_b[i] = false;
									},
									{ list_b[i] = true;
									});
							});
					});				
					
				^[list_a, list_b];
		}
		
		// eliminate this stage with a smart adding/removing scheme?
	reorderPartials { | thesePars |	/// JGL's bubble sort
		var temp;
		"REORDERING THE NEW LIST OF PARTIALS".postln;
           for ( 0, thesePars.size-2, 
            { | i |
                for ( thesePars.size-1, i+1,
                	{ | j |
                    if (thesePars[j].freq < thesePars[j-1].freq,
                    	{ temp = thesePars[j];
                        thesePars[j] = thesePars[j-1];
                        thesePars[j-1] = temp;
                    })
                })
            });		
		^thesePars
	}

}

/*
// Future Work //
Send each voice to 1 or 3 clusters, then the synth for that cluster reads the line in and decorrelates, AllPass filters the signal, etc. to differentiate the signal for 4 separate channels

Ampscale could ideally be one routing synth, which you can change the level of, rather than an ampscale for each partial
pass in a group on initializing the voice class? addaction for group?
GIVE SNAP SHOTS AN ID for handling multiple voices at once and naming in relevant way

*/